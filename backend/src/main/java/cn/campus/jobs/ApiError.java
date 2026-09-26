package cn.campus.jobs;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiError {
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<?> missing() { return ResponseEntity.status(404).body(Map.of("message", "接口不存在")); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> handle(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "请求失败" : e.getReason())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("message", "请检查输入格式和必填项")); }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> duplicate() { return ResponseEntity.status(409).body(Map.of("message", "该账号已注册或已经领取过该岗位")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unavailable(Exception e) {
        // Never log request bodies, credentials, or identity fields.
        org.slf4j.LoggerFactory.getLogger(ApiError.class).error("API failure type: {}", e.getClass().getName());
        return ResponseEntity.status(503).body(Map.of("message", "服务暂时不可用，请稍后重试"));
    }
}
