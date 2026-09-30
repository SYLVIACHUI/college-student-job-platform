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
    public record JobInput(@NotBlank @Size(max=100) String title,@NotBlank @Size(max=2000) String description,
        @NotBlank @Size(max=150) String location,@NotNull @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer=6,fraction=2) java.math.BigDecimal pay,
        @NotBlank @Size(max=40) String category,@NotNull @Min(1) @Max(200) Integer requiredCount,
        @NotBlank @Size(max=2000) String requirements,@NotNull @Future java.time.LocalDateTime startsAt,
        @NotNull @Min(1) @Max(525600) Integer durationMinutes) {}
    private final AuthService auth;
    private final UserRepository users;
    private final VerificationService verification;
    private final boolean secure;
    private final JobService jobs;
    public ApiController(AuthService auth,UserRepository users,VerificationService verification,JobService jobs,@Value("${app.secure-cookie}") boolean secure) {
        this.auth=auth;this.users=users;this.verification=verification;this.jobs=jobs;this.secure=secure;
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
        return jobs.list(auth.current(req));
    }
    @GetMapping("/jobs/{id}") public Object detail(@PathVariable String id,HttpServletRequest req) { return jobs.detail(id,auth.current(req)); }
    @PostMapping("/jobs") public Object publish(@Valid @RequestBody JobInput input,HttpServletRequest req) {
        String id=jobs.publish(input,auth.current(req));
        return Map.of("id",id,"message","岗位已发布，学生端可以查看");
    }
    public record CancelJob(@NotBlank @Size(max=300) String reason) {}
    @PostMapping("/jobs/{id}/withdraw") public Object withdraw(@PathVariable String id,HttpServletRequest req){jobs.withdraw(id,auth.current(req));return Map.of("message","已退出兼职");}
    @PostMapping("/jobs/{id}/cancel") public Object cancel(@PathVariable String id,@Valid @RequestBody CancelJob input,HttpServletRequest req){jobs.cancel(id,auth.current(req),input.reason());return Map.of("message","活动已取消");}
    @PostMapping("/jobs/{id}/apply") public Object apply(@PathVariable String id,HttpServletRequest req) {
        jobs.apply(id,auth.current(req));
        return Map.of("message","领取成功");
    }
}
