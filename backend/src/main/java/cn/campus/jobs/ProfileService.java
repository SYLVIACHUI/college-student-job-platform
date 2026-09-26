package cn.campus.jobs;

import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {
    private final UserRepository users;
    private static final Set<String> GRADES = Set.of("大一","大二","大三","大四","大五","硕士","博士");
    public ProfileService(UserRepository users) { this.users = users; }
    public static String displayName(Map<String,Object> user) {
        Object nick = user.get("nickname");
        return nick != null && !nick.toString().isBlank() ? nick.toString() :
            ("PUBLISHER".equals(user.get("role")) ? "企业用户 · " : "同学 · ") + user.get("id").toString().substring(0,8);
    }
    public static String avatarUrl(Map<String,Object> user) {
        return user.get("avatar_version") == null ? null : "/api/users/" + user.get("id") + "/avatar?v=" + user.get("avatar_version");
    }
    @Transactional
    public void update(String id, ProfileController.EditProfile input) {
        Map<String,Object> user = users.byId(id);
        if (input.birthday() != null && input.birthday().isBefore(java.time.LocalDate.of(1900,1,1))) bad("生日年份不能早于1900年");
        boolean student = "STUDENT".equals(user.get("role"));
        String grade = clean(input.grade());
        if (grade != null && !GRADES.contains(grade)) bad("请选择有效年级");
        if (!student && (grade != null || clean(input.major()) != null)) bad("企业账号不能设置学生学籍资料");
        users.jdbc().update("UPDATE app_user SET nickname=?,birthday=?,grade=?,major=?,bio=? WHERE id=?",
            input.nickname().trim(), input.birthday(), student ? grade : null, student ? clean(input.major()) : null, clean(input.bio()), id);
    }
    @Transactional
    public void avatar(String id, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) bad("头像大小需在2MB以内");
        byte[] result;
        try (var stream = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException();
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) bad("仅支持PNG或JPEG图片");
                reader.setInput(stream);
                int width=reader.getWidth(0), height=reader.getHeight(0);
                if (width<1 || height<1 || width>4096 || height>4096) bad("图片宽高不能超过4096像素");
                BufferedImage original = reader.read(0);
                BufferedImage output = new BufferedImage(256,256,BufferedImage.TYPE_INT_RGB);
                var graphics=output.createGraphics();
                graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,256,256);
                graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int size=Math.min(width,height), x=(width-size)/2,y=(height-size)/2;
                graphics.drawImage(original,0,0,256,256,x,y,x+size,y+size,null); graphics.dispose();
                var bytes=new ByteArrayOutputStream(); ImageIO.write(output,"png",bytes); result=bytes.toByteArray();
            } finally { reader.dispose(); }
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无法读取图片，请使用PNG或JPEG文件"); }
        // Serialize replacements per user; image bytes are re-encoded with metadata removed.
        users.jdbc().queryForMap("SELECT id FROM app_user WHERE id=? FOR UPDATE",id);
        users.jdbc().update("DELETE FROM user_avatar WHERE user_id=?",id);
        users.jdbc().update("INSERT INTO user_avatar(user_id,image_data) VALUES(?,?)",id,result);
        users.jdbc().update("UPDATE app_user SET avatar_version=? WHERE id=?",UUID.randomUUID().toString(),id);
    }
    public Map<String,Object> homepage(String id) {
        var rows=users.jdbc().queryForList("SELECT id,role,nickname,bio,grade,major,school,organization,verification_status,avatar_version,created_at FROM app_user WHERE id=?",id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"用户不存在");
        var profile=rows.get(0);
        profile.put("display_name",displayName(profile)); profile.put("avatar_url",avatarUrl(profile));
        // Organization/school are visible only after verified, never disclose submitted unreviewed identity data.
        if (!"APPROVED".equals(profile.get("verification_status"))) { profile.put("school",null);profile.put("organization",null); }
        profile.remove("avatar_version");
        return profile;
    }
    public Map<String,Object> history(Map<String,Object> user,int page) {
        if (page<0 || page>10000) bad("页码无效");
        boolean publisher="PUBLISHER".equals(user.get("role"));
        String id=user.get("id").toString();
        Long count=users.jdbc().queryForObject(publisher?"SELECT COUNT(*) FROM job WHERE publisher_id=?":"SELECT COUNT(*) FROM job_application WHERE student_id=?",Long.class,id);
        var rows=publisher ? users.jdbc().queryForList("SELECT j.*, (SELECT COUNT(*) FROM job_application a WHERE a.job_id=j.id) AS application_count FROM job j WHERE publisher_id=? ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",id,page*20)
            : users.jdbc().queryForList("SELECT a.id AS application_id,a.created_at AS accepted_at,j.id,j.title,j.location,j.pay,j.publisher_id,u.organization,p.amount_cents AS paid_cents,p.created_at AS paid_at FROM job_application a JOIN job j ON j.id=a.job_id JOIN app_user u ON u.id=j.publisher_id LEFT JOIN job_payment p ON p.application_id=a.id WHERE a.student_id=? ORDER BY a.created_at DESC,a.id DESC LIMIT 20 OFFSET ?",id,page*20);
        return Map.of("items",rows,"total",count,"page",page);
    }
    public Map<String,Object> applicants(String publisherId,String jobId,int page) {
        if (page<0 || page>10000) bad("页码无效");
        var jobs=users.jdbc().queryForList("SELECT * FROM job WHERE id=? AND publisher_id=?",jobId,publisherId);
        if(jobs.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在或无权查看");
        var rows=users.jdbc().queryForList("SELECT a.id AS application_id,a.created_at AS accepted_at,u.id,u.role,u.nickname,u.avatar_version,u.grade,u.major,p.amount_cents AS paid_cents FROM job_application a JOIN app_user u ON u.id=a.student_id LEFT JOIN job_payment p ON p.application_id=a.id WHERE a.job_id=? ORDER BY a.created_at DESC,a.id DESC LIMIT 20 OFFSET ?",jobId,page*20);
        for(var row:rows){row.put("display_name",displayName(row));row.put("avatar_url",avatarUrl(row));row.remove("avatar_version");}
        return Map.of("job",jobs.get(0),"items",rows,"total",users.jdbc().queryForObject("SELECT COUNT(*) FROM job_application WHERE job_id=?",Long.class,jobId),"page",page);
    }
    private String clean(String input){return input==null || input.isBlank()?null:input.trim();}
    private void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
