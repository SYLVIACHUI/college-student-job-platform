package cn.campus.jobs.controller;

import cn.campus.jobs.service.AuthService;
import cn.campus.jobs.service.CompanyService;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api")
public class CompanyController {
    public record EditCompany(@NotNull @Size(max=2000) String introduction){}
    private final AuthService auth;
    private final CompanyService companies;
    public CompanyController(AuthService auth,CompanyService companies){this.auth=auth;this.companies=companies;}
    @GetMapping("/companies/{id}") public Object home(@PathVariable String id,HttpServletRequest req){auth.current(req);return companies.home(id);}
    @GetMapping("/companies/{id}/jobs") public Object jobs(@PathVariable String id,@RequestParam(defaultValue="0") int page,HttpServletRequest req){auth.current(req);return companies.jobs(id,page);}
    @GetMapping("/companies/{id}/photos/{photoId}") public ResponseEntity<byte[]> photo(@PathVariable String id,@PathVariable String photoId,HttpServletRequest req){
        auth.current(req);return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(companies.photo(id,photoId));
    }
    @PutMapping("/me/company") public Object edit(@Valid @RequestBody EditCompany input,HttpServletRequest req){
        String id=companies.owner(auth.current(req));companies.edit(id,input.introduction());return companies.home(id);
    }
    @PostMapping("/me/company/photos") public Object upload(@RequestParam("file") MultipartFile file,HttpServletRequest req){
        String id=companies.owner(auth.current(req));companies.upload(id,file);return companies.home(id);
    }
    @DeleteMapping("/me/company/photos/{photoId}") public Object delete(@PathVariable String photoId,HttpServletRequest req){
        companies.delete(companies.owner(auth.current(req)),photoId);return Map.of("deleted",true);
    }
}
