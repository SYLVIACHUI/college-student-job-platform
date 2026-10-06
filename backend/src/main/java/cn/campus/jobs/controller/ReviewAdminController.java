package cn.campus.jobs.controller;

import cn.campus.jobs.service.ReviewAdminService;

import java.time.Duration;
import java.util.Map;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin")
public class ReviewAdminController {
    public record Login(@NotBlank @Size(max=60) String username,@NotBlank @Size(max=64) String password){}
    public record Decision(@NotBlank @Size(max=36) String reviewId,@NotNull Boolean approved,@NotBlank @Size(max=500) String note){}
    private final ReviewAdminService service;private final boolean secure;
    public ReviewAdminController(ReviewAdminService service,@Value("${app.secure-cookie}") boolean secure){this.service=service;this.secure=secure;}
    @ModelAttribute public void noCache(HttpServletResponse res){res.setHeader("Cache-Control","no-store");}
    private void cookie(HttpServletResponse res,String token,long hours){res.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("CAMPUS_REVIEW_SESSION",token).path("/api/admin").httpOnly(true).secure(secure).sameSite("Strict").maxAge(Duration.ofHours(hours)).build().toString());}
    @PostMapping("/login") public Object login(@Valid @RequestBody Login input,HttpServletRequest req,HttpServletResponse res){String token=service.login(input.username(),input.password(),req.getRemoteAddr());service.logout(req);cookie(res,token,4);return Map.of("message","审核端登录成功");}
    @PostMapping("/logout") public Object logout(HttpServletRequest req,HttpServletResponse res){service.logout(req);cookie(res,"",0);return Map.of("message","已退出");}
    @GetMapping("/me") public Object me(HttpServletRequest req){return service.current(req);}
    @GetMapping("/reviews") public Object list(@RequestParam(defaultValue="ALL") String role,@RequestParam(defaultValue="0") int page,HttpServletRequest req){service.current(req);return service.pending(role,page);}
    @GetMapping("/reviews/{id}") public Object detail(@PathVariable String id,HttpServletRequest req){service.current(req);return service.detail(id);}
    @PostMapping("/reviews/{id}/decision") public Object decision(@PathVariable String id,@Valid @RequestBody Decision input,HttpServletRequest req){var admin=service.current(req);service.decide(admin.get("id").toString(),id,input.reviewId(),input.approved(),input.note());return Map.of("message",input.approved()?"已通过，用户操作权限已开通":"已驳回，用户可修改资料后重新提交");}
    @GetMapping("/history") public Object history(@RequestParam(defaultValue="0") int page,HttpServletRequest req){service.current(req);return service.history(page);}
}
