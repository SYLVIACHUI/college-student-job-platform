package cn.campus.jobs;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/notifications")
public class NotificationController {
    private final AuthService auth;
    private final NotificationService messages;
    public NotificationController(AuthService auth,NotificationService messages){this.auth=auth;this.messages=messages;}
    private String user(HttpServletRequest req){return auth.current(req).get("id").toString();}
    @GetMapping public Object list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="false") boolean unreadOnly,HttpServletRequest req){return messages.list(user(req),page,unreadOnly);}
    @GetMapping("/unread-count") public Object unread(HttpServletRequest req){return Map.of("unread",messages.unread(user(req)));}
    @PostMapping("/{id}/read") public Object read(@PathVariable String id,HttpServletRequest req){messages.read(user(req),id);return Map.of("read",true);}
    @PostMapping("/read-all") public Object all(HttpServletRequest req){messages.readAll(user(req));return Map.of("read",true);}
}
