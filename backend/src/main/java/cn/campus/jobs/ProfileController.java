package cn.campus.jobs;

import java.time.LocalDate;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api")
public class ProfileController {
    public record EditProfile(@NotBlank @Size(max=40) String nickname,@PastOrPresent LocalDate birthday,
        @Size(max=10) String grade,@Size(max=100) String major,@Size(max=300) String bio) {}
    private final AuthService auth; private final ProfileService profiles; private final UserRepository users;
    public ProfileController(AuthService auth,ProfileService profiles,UserRepository users){this.auth=auth;this.profiles=profiles;this.users=users;}
    @PutMapping("/me/profile") public Object update(@Valid @RequestBody EditProfile input,HttpServletRequest req){profiles.update(auth.current(req).get("id").toString(),input);return auth.publicUser(auth.current(req));}
    @PostMapping("/me/avatar") public Object upload(@RequestParam("file") MultipartFile file,HttpServletRequest req){profiles.avatar(auth.current(req).get("id").toString(),file);return auth.publicUser(auth.current(req));}
    @GetMapping("/users/{id}/avatar") public ResponseEntity<byte[]> avatar(@PathVariable String id,HttpServletRequest req){
        auth.current(req);
        var rows=users.jdbc().query("SELECT image_data FROM user_avatar WHERE user_id=?",(r,n)->r.getBytes(1),id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"暂无头像");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(rows.get(0));
    }
    @GetMapping("/users/{id}") public Object home(@PathVariable String id,HttpServletRequest req){auth.current(req);return profiles.homepage(id);}
    @GetMapping("/me/history") public Object history(@RequestParam(defaultValue="0") int page,HttpServletRequest req){return profiles.history(auth.current(req),page);}
    @GetMapping("/jobs/{id}/applicants") public Object applicants(@PathVariable String id,@RequestParam(defaultValue="0") int page,HttpServletRequest req){return profiles.applicants(auth.current(req).get("id").toString(),id,page);}
}
