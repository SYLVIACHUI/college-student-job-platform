package cn.campus.jobs.service;

import cn.campus.jobs.mapper.ReviewAdminMapper;

import cn.campus.jobs.mapper.ExpiringStore;
import cn.campus.jobs.mapper.UserRepository;

import java.time.Duration;
import java.util.*;
import java.security.SecureRandom;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewAdminService {
    private final ReviewAdminMapper mapper;
    private final UserRepository users; private final Crypto crypto; private final ExpiringStore store;
    private final AuthService auth; private final VerificationService verification;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    private final String dummy=passwords.encode("review-dummy-password");
    public ReviewAdminService(UserRepository users,Crypto crypto,ExpiringStore store,AuthService auth,VerificationService verification,ReviewAdminMapper mapper){ this.mapper=mapper;this.users=users;this.crypto=crypto;this.store=store;this.auth=auth;this.verification=verification;}
    public String login(String username,String password,String ip){
        auth.limit("review-login-ip:"+crypto.hash(ip),30,Duration.ofMinutes(15));
        auth.limit("review-login-user:"+crypto.hash(username),10,Duration.ofMinutes(15));
        var rows=mapper.account(username);
        var user=rows.isEmpty()?null:rows.get(0);
        boolean matches=passwords.matches(password,user==null?dummy:user.get("password_hash").toString());
        if(user==null||!matches||((Number)user.get("enabled")).intValue()!=1)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"审核账号或密码错误");
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.put("review-session:"+crypto.hash(token),user.get("id").toString(),Duration.ofHours(4));return token;
    }
    private String token(HttpServletRequest req){if(req.getCookies()!=null)for(var c:req.getCookies())if("CAMPUS_REVIEW_SESSION".equals(c.getName()))return c.getValue();return "";}
    public Map<String,Object> current(HttpServletRequest req){
        String id=store.get("review-session:"+crypto.hash(token(req)));
        var rows=id==null?List.<Map<String,Object>>of():mapper.current(id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请登录审核端");return rows.get(0);
    }
    public void logout(HttpServletRequest req){store.delete("review-session:"+crypto.hash(token(req)));}
    public Object pending(String role,int page){
        if(!Set.of("ALL","PUBLISHER","STUDENT").contains(role)||page<0||page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"查询条件无效");
        long total=mapper.pendingCount(role);
        var items=mapper.pending(role,page*20);
        return Map.of("items",items,"total",total,"page",page);
    }
    public Object detail(String id){
        var account=users.findAccountById(id);if(account==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"用户不存在");
        var user=account.toMap();if(!"PENDING".equals(user.get("verification_status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"该申请已处理，请刷新列表");
        Map<String,Object> out=new LinkedHashMap<>();
        for(String field:List.of("id","role","nickname","organization","surname","school","review_id","verification_status"))out.put(field,user.get(field));
        for(var entry:Map.of("name","name_cipher","identityNumber","identity_cipher","studentNumber","student_number_cipher").entrySet())out.put(entry.getKey(),user.get(entry.getValue())==null?null:crypto.decrypt(user.get(entry.getValue()).toString()));
        out.put("events",mapper.events(id));return out;
    }
    @Transactional
    public void decide(String adminId,String id,String reviewId,boolean approved,String note){
        verification.applyDecision(id,reviewId,approved,note.trim());
        mapper.insertAction(UUID.randomUUID().toString(),adminId,id,reviewId,approved?"APPROVED":"REJECTED",note.trim());
    }
    public Object history(int page){
        if(page<0||page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码无效");
        var items=mapper.history(page*20);
        return Map.of("items",items,"page",page,"total",mapper.historyCount());
    }
}
