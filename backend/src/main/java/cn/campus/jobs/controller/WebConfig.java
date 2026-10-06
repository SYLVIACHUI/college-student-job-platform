package cn.campus.jobs.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class WebConfig implements WebMvcConfigurer {
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                res.setHeader("Cache-Control", "no-store");
                res.setHeader("X-Content-Type-Options", "nosniff");
                res.setHeader("X-Frame-Options", "DENY");
                // No cross-origin API permissions. A custom header forces cross-origin preflight.
                if (!java.util.Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod()) && !"campus-web".equals(req.getHeader("X-Requested-With"))) {
                    res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"请求来源校验失败\"}"); return false;
                }
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}
