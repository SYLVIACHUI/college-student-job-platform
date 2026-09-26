package cn.campus.jobs;

import java.time.Duration;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api")
public class ApiController {
    public record Code(@NotNull @Pattern(regexp="PUBLISHER|STUDENT") String role, @NotNull @Pattern(regexp="1[3-9][0-9]{9}") String phone) {}
    public record Register(@NotNull @Pattern(regexp="PUBLISHER|STUDENT") String role, @NotNull @Pattern(regexp="1[3-9][0-9]{9}") String phone, @NotNull @Pattern(regexp="[0-9]{6}") String code, @NotNull @Size(min=8,max=64) @Pattern(regexp="(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]+") String password) {}
    public record Login(@NotNull @Pattern(regexp="PUBLISHER|STUDENT") String role, @NotNull @Pattern(regexp="1[3-9][0-9]{9}") String phone, @NotBlank @Size(max=64) String password) {}
    public record Verification(@Size(max=150) String organization,@Size(max=40) String surname,@Size(max=80) String name,@Size(max=18) String identityNumber,@Size(max=150) String school,@Size(max=50) String studentNumber,@Email @Size(max=254) String email) {}
    public record JobInput(@NotBlank @Size(max=100) String title,@NotBlank @Size(max=2000) String description,@NotBlank @Size(max=150) String location,@Min(1) @Max(100000) int pay) {}
    private final AuthService auth;
    private final UserRepository users;
    private final VerificationService verification;
    private final boolean secure;
    public ApiController(AuthService auth,UserRepository users,VerificationService verification,@Value("${app.secure-cookie}") boolean secure) {
        this.auth=auth;this.users=users;this.verification=verification;this.secure=secure;
    }
    @GetMapping("/config") public Object config() { return Map.of("development",auth.dev(),"reviewMode","PENDING_INTEGRATION"); }
    @PostMapping("/auth/code") public Object code(@Valid @RequestBody Code input,HttpServletRequest req) { return auth.sendCode(input.role(),input.phone(),req.getRemoteAddr()); }
    @PostMapping("/auth/register") public Object register(@Valid @RequestBody Register input) { return auth.register(input.role(),input.phone(),input.code(),input.password()); }
    @PostMapping("/auth/login") public Object login(@Valid @RequestBody Login input,HttpServletRequest req,HttpServletResponse res) {
        String token=auth.login(input.role(),input.phone(),input.password(),req.getRemoteAddr());
        auth.logout(req);
        cookie(res,token,Duration.ofHours(12)); return Map.of("message","登录成功");
    }
    @PostMapping("/auth/logout") public Object logout(HttpServletRequest req,HttpServletResponse res) { auth.logout(req);cookie(res,"",Duration.ZERO);return Map.of("message","已退出登录"); }
    private void cookie(HttpServletResponse res,String token,Duration ttl) { res.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("CAMPUS_SESSION",token).httpOnly(true).secure(secure).sameSite("Strict").path("/api").maxAge(ttl).build().toString()); }
    @GetMapping("/me") public Object me(HttpServletRequest req) { return auth.publicUser(auth.current(req)); }
    @PostMapping("/verification") public Object verify(@Valid @RequestBody Verification input,HttpServletRequest req) { verification.submit((String)auth.current(req).get("id"),input);return Map.of("message","认证资料已提交，等待审核"); }
    @GetMapping("/verification/events") public Object events(HttpServletRequest req) { return users.jdbc().queryForList("SELECT id,status,note,created_at FROM verification_event WHERE user_id=? ORDER BY created_at DESC",auth.current(req).get("id")); }
    @GetMapping("/jobs") public Object jobs(HttpServletRequest req) {
        Map<String,Object> user=auth.current(req);
        if (user.get("role").equals("PUBLISHER")) return users.jdbc().queryForList("SELECT j.*,u.organization,(SELECT COUNT(*) FROM job_application a WHERE a.job_id=j.id) AS applications FROM job j JOIN app_user u ON u.id=j.publisher_id WHERE j.publisher_id=? ORDER BY j.created_at DESC LIMIT 100",user.get("id"));
        return users.jdbc().queryForList("SELECT j.*,u.organization,CASE WHEN a.id IS NULL THEN 0 ELSE 1 END AS applied FROM job j JOIN app_user u ON u.id=j.publisher_id LEFT JOIN job_application a ON a.job_id=j.id AND a.student_id=? ORDER BY j.created_at DESC LIMIT 100",user.get("id"));
    }
    @PostMapping("/jobs") public Object publish(@Valid @RequestBody JobInput input,HttpServletRequest req) {
        Map<String,Object> user=auth.current(req); permission(user,"PUBLISHER","can_publish");
        String id=UUID.randomUUID().toString();
        users.jdbc().update("INSERT INTO job(id,publisher_id,title,description,location,pay) VALUES(?,?,?,?,?,?)",id,user.get("id"),input.title(),input.description(),input.location(),input.pay());
        return Map.of("id",id,"message","岗位已发布，学生端可以查看");
    }
    @PostMapping("/jobs/{id}/apply") public Object apply(@PathVariable String id,HttpServletRequest req) {
        Map<String,Object> user=auth.current(req); permission(user,"STUDENT","can_accept");
        if (users.jdbc().queryForObject("SELECT COUNT(*) FROM job WHERE id=?",Integer.class,id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在");
        users.jdbc().update("INSERT INTO job_application(id,job_id,student_id) VALUES(?,?,?)",UUID.randomUUID().toString(),id,user.get("id"));
        return Map.of("message","领取成功");
    }
    private void permission(Map<String,Object> user,String role,String flag) {
        if (!role.equals(user.get("role")) || !"APPROVED".equals(user.get("verification_status")) || ((Number)user.get(flag)).intValue()!=1) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前身份未获得权限，请先完成实名认证并等待审核通过");
    }
}
