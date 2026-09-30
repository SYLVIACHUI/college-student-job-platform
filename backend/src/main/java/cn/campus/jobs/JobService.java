package cn.campus.jobs;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {
    private final UserRepository users;
    private final NotificationService notifications;
    public JobService(UserRepository users,NotificationService notifications) { this.users=users;this.notifications=notifications; }
    private static final String COUNTS="(SELECT COUNT(*) FROM job_application a WHERE a.job_id=j.id AND a.status='ACTIVE')";
    private static final String SUMMARY="j.id,j.publisher_id,j.status,j.title,j.category,j.required_count,j.starts_at,j.duration_minutes,u.organization,"+COUNTS+" AS applications,j.required_count-"+COUNTS+" AS remaining,CASE WHEN "+COUNTS+">0 THEN 'ACCEPTED' ELSE 'UNACCEPTED' END AS acceptance_status";
    public Map<String,Object> companyJobs(String publisherId,int page){
        if(page<0 || page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码无效");
        var now=java.time.LocalDateTime.now();
        String condition=" WHERE j.publisher_id=? AND j.status='OPEN' AND j.starts_at>?";
        var items=users.jdbc().queryForList("SELECT "+SUMMARY+" FROM job j JOIN publisher_user u ON u.id=j.publisher_id"+condition+" ORDER BY j.created_at DESC,j.id DESC LIMIT 12 OFFSET ?",publisherId,now,page*12);
        Long total=users.jdbc().queryForObject("SELECT COUNT(*) FROM job j"+condition,Long.class,publisherId,now);
        return Map.of("items",items,"total",total,"page",page);
    }
    public List<Map<String,Object>> list(Map<String,Object> viewer) {
        String condition="PUBLISHER".equals(viewer.get("role"))?" WHERE j.publisher_id=?":" WHERE j.status='OPEN'";
        var args=new ArrayList<Object>();args.add(viewer.get("id"));if("PUBLISHER".equals(viewer.get("role")))args.add(viewer.get("id"));
        return users.jdbc().queryForList("SELECT "+SUMMARY+",CASE WHEN EXISTS(SELECT 1 FROM job_application mine WHERE mine.job_id=j.id AND mine.student_id=? AND mine.status='ACTIVE') THEN 1 ELSE 0 END AS applied FROM job j JOIN publisher_user u ON u.id=j.publisher_id"+condition+" ORDER BY j.created_at DESC,j.id DESC LIMIT 100",args.toArray());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(String id,Map<String,Object> viewer) {
        var rows=users.jdbc().queryForList("SELECT j.*,u.organization FROM job j JOIN publisher_user u ON u.id=j.publisher_id WHERE j.id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在");
        var job=rows.get(0);
        // Explicit public projection: never fetch student identity data into this response.
        var people=users.jdbc().queryForList("SELECT u.id,u.nickname,u.avatar_version FROM job_application a JOIN student_user u ON u.id=a.student_id WHERE a.job_id=? AND a.status='ACTIVE' ORDER BY a.created_at,a.id",id);
        for(var person:people){person.put("display_name",ProfileService.displayName(person));person.put("avatar_url",ProfileService.avatarUrl(person));person.remove("avatar_version");}
        job.put("participants",people);job.put("applications",people.size());
        job.put("remaining",Math.max(0,((Number)job.get("required_count")).intValue()-people.size()));
        job.put("acceptance_status",people.isEmpty()?"UNACCEPTED":"ACCEPTED");
        job.put("applied",people.stream().anyMatch(p->p.get("id").equals(viewer.get("id"))));
        var mine=users.jdbc().queryForList("SELECT a.status,CASE WHEN p.id IS NULL THEN 0 ELSE 1 END AS paid FROM job_application a LEFT JOIN job_payment p ON p.application_id=a.id WHERE a.job_id=? AND a.student_id=?",id,viewer.get("id"));
        job.put("application_status",mine.isEmpty()?null:mine.get(0).get("status"));
        job.put("paid",!mine.isEmpty()&&((Number)mine.get(0).get("paid")).intValue()==1);
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
        var rows=users.jdbc().queryForList("SELECT required_count,starts_at,status,publisher_id,title FROM job WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在");
        var job=rows.get(0);
        if(!"OPEN".equals(job.get("status")))conflict("活动已取消，不能报名");
        if(users.jdbc().queryForObject("SELECT COUNT(*) FROM job_application WHERE job_id=? AND student_id=?",Integer.class,id,user.get("id"))>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"该兼职已有你的报名记录；退出后不支持再次报名");
        if(job.get("starts_at") instanceof java.sql.Timestamp start && !start.toLocalDateTime().isAfter(java.time.LocalDateTime.now()))throw new ResponseStatusException(HttpStatus.CONFLICT,"兼职已开始，不能继续加入");
        int count=users.jdbc().queryForObject("SELECT COUNT(*) FROM job_application WHERE job_id=? AND status='ACTIVE'",Integer.class,id);
        if(count>=((Number)job.get("required_count")).intValue())throw new ResponseStatusException(HttpStatus.CONFLICT,"名额已满，请选择其他兼职");
        String applicationId=UUID.randomUUID().toString();
        users.jdbc().update("INSERT INTO job_application(id,job_id,student_id) VALUES(?,?,?)",applicationId,id,user.get("id"));
        notifications.send(job.get("publisher_id").toString(),"joined:"+applicationId,"JOB_JOINED","有同学参加兼职",ProfileService.displayName(user)+" 已参加「"+job.get("title")+"」。","JOB",id);
    }
    @Transactional
    public void withdraw(String id,Map<String,Object> user){
        if(!"STUDENT".equals(user.get("role")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅学生可以退出报名");
        var job=lockedJob(id);
        var apps=users.jdbc().queryForList("SELECT id,status FROM job_application WHERE job_id=? AND student_id=? FOR UPDATE",id,user.get("id"));
        if(apps.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"你尚未报名此兼职");
        var app=apps.get(0);
        if("WITHDRAWN".equals(app.get("status")))return;
        if(!"OPEN".equals(job.get("status")))conflict("活动已取消，无需退出");
        beforeStart(job);
        if(users.jdbc().queryForObject("SELECT COUNT(*) FROM job_payment WHERE application_id=?",Integer.class,app.get("id"))>0)conflict("已结算的报名不能退出");
        users.jdbc().update("UPDATE job_application SET status='WITHDRAWN',withdrawn_at=CURRENT_TIMESTAMP WHERE id=?",app.get("id"));
        notifications.send(job.get("publisher_id").toString(),"withdrawn:"+app.get("id"),"JOB_WITHDRAWN","有同学退出兼职",ProfileService.displayName(user)+" 已退出「"+job.get("title")+"」，名额已释放。","JOB",id);
    }
    @Transactional
    public void cancel(String id,Map<String,Object> user,String reason){
        var job=lockedJob(id);
        if(!user.get("id").equals(job.get("publisher_id")))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在或无权取消");
        if("CANCELLED".equals(job.get("status")))return;
        beforeStart(job);
        if(users.jdbc().queryForObject("SELECT COUNT(*) FROM job_payment p JOIN job_application a ON a.id=p.application_id WHERE a.job_id=?",Integer.class,id)>0)conflict("已有报酬结算的活动不能直接取消");
        var people=users.jdbc().queryForList("SELECT student_id FROM job_application WHERE job_id=? AND status='ACTIVE'",id);
        users.jdbc().update("UPDATE job SET status='CANCELLED',cancel_reason=?,cancelled_at=CURRENT_TIMESTAMP WHERE id=?",reason.trim(),id);
        for(var person:people)notifications.send(person.get("student_id").toString(),"cancelled:"+id,"JOB_CANCELLED","你报名的活动已取消","「"+job.get("title")+"」已取消。原因："+reason.trim(),"JOB",id);
    }
    private Map<String,Object> lockedJob(String id){
        var rows=users.jdbc().queryForList("SELECT * FROM job WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在");
        return rows.get(0);
    }
    private void beforeStart(Map<String,Object> job){
        if(!(job.get("starts_at") instanceof java.sql.Timestamp start)||!start.toLocalDateTime().isAfter(java.time.LocalDateTime.now()))conflict("仅允许在兼职开始前操作；未设置开始时间的旧岗位暂不支持");
    }
    private void conflict(String message){throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private void permission(Map<String,Object> user,String role,String flag) {
        if(!role.equals(user.get("role"))||!"APPROVED".equals(user.get("verification_status"))||((Number)user.get(flag)).intValue()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前身份未获得权限，请先完成实名认证并等待审核通过");
    }
}
