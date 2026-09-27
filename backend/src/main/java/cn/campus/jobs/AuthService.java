package cn.campus.jobs;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final Crypto crypto;
    private final ExpiringStore store;
    private final boolean dev;
    private final SmsSender sms;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String dummyHash = passwords.encode("timing-equalization-password");
    private final SecureRandom random = new SecureRandom();
    public AuthService(UserRepository users, Crypto crypto, ExpiringStore store, Environment env, SmsSender sms) {
        this.users = users; this.crypto = crypto; this.store = store; this.dev = env.acceptsProfiles(Profiles.of("dev")); this.sms = sms;
    }
    public boolean dev() { return dev; }
    public void limit(String key, int max, Duration ttl) {
        if (store.increment("limit:" + key, ttl) > max) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "操作太频繁，请稍后重试");
    }
    public Map<String,Object> sendCode(String role, String phone, String ip) {
        limit("sms-ip:" + crypto.hash(ip), 20, Duration.ofHours(1));
        String key = role + ":" + crypto.hash(phone);
        limit("sms-minute:" + key, 1, Duration.ofSeconds(60));
        limit("sms-day:" + key, 10, Duration.ofDays(1));
        String code = String.format("%06d", random.nextInt(1000000));
        store.put("otp:" + key, crypto.hash(code), Duration.ofMinutes(5));
        try { sms.send(phone, code); }
        catch (RuntimeException e) { store.delete("otp:" + key); throw e; }
        if (dev) return Map.of("message", "开发验证码已生成（有效期5分钟）", "debugCode", code, "expiresIn", 300);
        return Map.of("message", "验证码已发送", "expiresIn", 300);
    }
    @org.springframework.transaction.annotation.Transactional
    public Map<String,Object> register(String role, String phone, String code, String password) {
        String hash = crypto.hash(phone);
        limit("otp-attempt:" + role + ":" + hash, 5, Duration.ofMinutes(5));
        if (!store.consume("otp:" + role + ":" + hash, crypto.hash(code))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码错误、已使用或已过期");
        String id = UUID.randomUUID().toString();
        users.create(id, role, hash, crypto.encrypt(phone), passwords.encode(password));
        users.jdbc().update("INSERT INTO wallet(user_id) VALUES(?)",id);
        return Map.of("message", "注册成功，请使用手机号登录", "account", phone);
    }
    public String login(String role, String phone, String password, String ip) {
        limit("login-ip:" + crypto.hash(ip), 60, Duration.ofMinutes(15));
        String hash = crypto.hash(phone);
        limit("login-account:" + role + ":" + hash, 15, Duration.ofMinutes(15));
        Map<String,Object> user = users.byAccount(role, hash);
        boolean matches = passwords.matches(password, user == null ? dummyHash : (String) user.get("password_hash"));
        if (user == null || !matches) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误，请确认所选身份");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.put("session:" + crypto.hash(token), (String) user.get("id"), Duration.ofHours(12));
        users.jdbc().update("UPDATE " + users.tableForId(user.get("id").toString()) + " SET last_login_at=CURRENT_TIMESTAMP WHERE id=?", user.get("id"));
        return token;
    }
    public String token(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (cookie.getName().equals("CAMPUS_SESSION")) return cookie.getValue();
        return "";
    }
    public Map<String,Object> current(HttpServletRequest request) {
        String id = store.get("session:" + crypto.hash(token(request)));
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return users.byId(id);
    }
    public void logout(HttpServletRequest request) { store.delete("session:" + crypto.hash(token(request))); }
    public Map<String,Object> publicUser(Map<String,Object> user) {
        Map<String,Object> out = new LinkedHashMap<>();
        for (String field : List.of("id", "role", "email", "organization", "school", "verification_status", "review_note", "review_id", "can_publish", "can_accept", "last_login_at", "created_at")) out.put(field, user.get(field));
        String phone = crypto.decrypt((String)user.get("phone_cipher"));
        out.put("account", phone.substring(0,3) + "****" + phone.substring(7));
        out.put("phone", out.get("account"));
        for (String field : List.of("nickname", "birthday", "grade", "major", "bio")) out.put(field, user.get(field));
        out.put("display_name", ProfileService.displayName(user));
        out.put("avatar_url", ProfileService.avatarUrl(user));
        out.put("publish_count", users.jdbc().queryForObject("SELECT COUNT(*) FROM job WHERE publisher_id=?", Long.class, user.get("id")));
        return out;
    }
}
