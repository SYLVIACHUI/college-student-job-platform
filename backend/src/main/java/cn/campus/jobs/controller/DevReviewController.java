package cn.campus.jobs.controller;

import cn.campus.jobs.service.AuthService;
import cn.campus.jobs.service.VerificationService;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/** Available only in explicitly enabled local development mode; this is NOT an AI reviewer. */
@RestController @Profile("dev") @RequestMapping("/api/dev")
public class DevReviewController {
    public record Decision(@NotBlank String reviewId,@NotNull Boolean approved) {}
    private final AuthService auth;
    private final VerificationService service;
    public DevReviewController(AuthService auth,VerificationService service) { this.auth=auth;this.service=service; }
    @PostMapping("/review") public Object review(@Valid @RequestBody Decision input,HttpServletRequest req) {
        service.applyDecision((String)auth.current(req).get("id"),input.reviewId(),input.approved(),input.approved()?"开发模拟：审核通过（非真实实名认证）":"开发模拟：资料需补充，请核对后重新提交");
        return Map.of("message","开发模拟审核已完成");
    }
}
