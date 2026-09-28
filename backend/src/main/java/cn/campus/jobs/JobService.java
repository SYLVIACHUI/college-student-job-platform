package cn.campus.jobs;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {
    private final UserRepository users;
    public JobService(UserRepository users) { this.users=users; }
    private static final String COUNTS="(SELECT COUNT(*) FROM job_application a WHERE a.job_id=j.id)";
    private static final String SUMMARY="j.id,j.publisher_id,j.title,j.category,j.required_count,j.starts_at,j.duration_minutes,u.organization,"+COUNTS+" AS applications,j.required_count-"+COUNTS+" AS remaining,CASE WHEN "+COUNTS+">0 THEN 'ACCEPTED' ELSE 'UNACCEPTED' END AS acceptance_status";
    public Map<String,Object> companyJobs(String publisherId,int page){
        if(page<0 || page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码无效");
        var now=java.time.LocalDateTime.now();
        String condition=" WHERE j.publisher_id=? AND j.starts_at>?";
        var items=users.jdbc().queryForList("SELECT "+SUMMARY+" FROM job j JOIN publisher_user u ON u.id=j.publisher_id"+condition+" ORDER BY j.created_at DESC,j.id DESC LIMIT 12 OFFSET ?",publisherId,now,page*12);
        Long total=users.jdbc().queryForObject("SELECT COUNT(*) FROM job j"+condition,Long.class,publisherId,now);
        return Map.of("items",items,"total",total,"page",page);
    }
    public List<Map<String,Object>> list(Map<String,Object> viewer) {
        String condition="PUBLISHER".equals(viewer.get("role"))?" WHERE j.publisher_id=?":"";
        var args=new ArrayList<Object>();args.add(viewer.get("id"));if(!condition.isEmpty())args.add(viewer.get("id"));
        return users.jdbc().queryForList("SELECT "+SUMMARY+",CASE WHEN EXISTS(SELECT 1 FROM job_application mine WHERE mine.job_id=j.id AND mine.student_id=?) THEN 1 ELSE 0 END AS applied FROM job j JOIN publisher_user u ON u.id=j.publisher_id"+condition+" ORDER BY j.created_at DESC,j.id DESC LIMIT 100",args.toArray());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(String id,Map<String,Object> viewer) {
        var rows=users.jdbc().queryForList("SELECT j.*,u.organization FROM job j JOIN publisher_user u ON u.id=j.publisher_id WHERE j.id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在");
        var job=rows.get(0);
        // Explicit public projection: never fetch student identity data into this response.
        var people=users.jdbc().queryForList("SELECT u.id,u.nickname,u.avatar_version FROM job_application a JOIN student_user u ON u.id=a.student_id WHERE a.job_id=? ORDER BY a.created_at,a.id",id);
        for(var person:people){person.put("display_name",ProfileService.displayName(person));person.put("avatar_url",ProfileService.avatarUrl(person));person.remove("avatar_version");}
        job.put("participants",people);job.put("applications",people.size());
        job.put("remaining",Math.max(0,((Number)job.get("required_count")).intValue()-people.size()));
        job.put("acceptance_status",people.isEmpty()?"UNACCEPTED":"ACCEPTED");
        job.put("applied",people.stream().anyMatch(p->p.get("id").equals(viewer.get("id"))));
        return job;
    }
    public String publish(ApiController.JobInput input,Map<String,Object> user) {
        permission(user,"PUBLISHER","can_publish");
        Job job=new Job(UUID.randomUUID().toString(),user.get("id").toString(),input.title().trim(),input.category().trim(),input.requiredCount(),input.description().trim(),input.requirements().trim(),input.location().trim(),input.pay(),"TOTAL",input.startsAt(),input.durationMinutes());
        users.jdbc().update("INSERT INTO job(id,publisher_id,title,category,required_count,description,requirements,location,pay,pay_unit,starts_at,duration_minutes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",job.id(),job.publisherId(),job.title(),job.category(),job.requiredCount(),job.description(),job.requirements(),job.location(),job.pay(),job.payUnit(),job.startsAt(),job.durationMinutes());
        return job.id();
    }
    @Transactional
    public void apply(String id,Map<String,Object> user) {
        permission(user,"STUDENT","can_accept");
        var rows=users.jdbc().queryForList("SELECT required_count,starts_at FROM job WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在");
        var job=rows.get(0);
        if(users.jdbc().queryForObject("SELECT COUNT(*) FROM job_application WHERE job_id=? AND student_id=?",Integer.class,id,user.get("id"))>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"你已经加入该兼职");
        if(job.get("starts_at") instanceof java.sql.Timestamp start && !start.toLocalDateTime().isAfter(java.time.LocalDateTime.now()))throw new ResponseStatusException(HttpStatus.CONFLICT,"兼职已开始，不能继续加入");
        int count=users.jdbc().queryForObject("SELECT COUNT(*) FROM job_application WHERE job_id=?",Integer.class,id);
        if(count>=((Number)job.get("required_count")).intValue())throw new ResponseStatusException(HttpStatus.CONFLICT,"名额已满，请选择其他兼职");
        users.jdbc().update("INSERT INTO job_application(id,job_id,student_id) VALUES(?,?,?)",UUID.randomUUID().toString(),id,user.get("id"));
    }
    private void permission(Map<String,Object> user,String role,String flag) {
        if(!role.equals(user.get("role"))||!"APPROVED".equals(user.get("verification_status"))||((Number)user.get(flag)).intValue()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前身份未获得权限，请先完成实名认证并等待审核通过");
    }
}
