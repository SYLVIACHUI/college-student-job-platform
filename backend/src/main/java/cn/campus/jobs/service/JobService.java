package cn.campus.jobs.service;

import cn.campus.jobs.mapper.JobMapper;

import cn.campus.jobs.controller.ApiController;
import cn.campus.jobs.entity.Job;
import cn.campus.jobs.mapper.UserRepository;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;

@Service
public class JobService {
    private final JobMapper mapper;
    private final UserRepository users;
    private final NotificationService notifications;
    private final Crypto crypto;
    public JobService(UserRepository users,NotificationService notifications,Crypto crypto,JobMapper mapper) { this.mapper=mapper; this.users=users;this.notifications=notifications;this.crypto=crypto; }
    public Map<String,Object> companyJobs(String publisherId,int page){
        if(page<0 || page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码无效");
        var now=java.time.LocalDateTime.now();
        var items=mapper.companyJobs(publisherId,now,page*12);
        Long total=mapper.companyJobCount(publisherId,now);
        return Map.of("items",items,"total",total,"page",page);
    }
    public List<Map<String,Object>> list(Map<String,Object> viewer) {
        return mapper.listJobs(viewer.get("id"),"PUBLISHER".equals(viewer.get("role")));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(String id,Map<String,Object> viewer) {
        var rows=mapper.detail(id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在");
        var job=rows.get(0);
        // Explicit public projection: never fetch student identity data into this response.
        var people=mapper.participants(id);
        for(var person:people){person.put("display_name",ProfileService.displayName(person));person.put("avatar_url",ProfileService.avatarUrl(person));person.remove("avatar_version");}
        job.put("participants",people);job.put("accepted_count",people.size());
        job.put("applications",mapper.registeredCount(id));
        job.put("remaining",((Number)job.get("remaining_count")).intValue());
        job.put("acceptance_status",people.isEmpty()?"UNACCEPTED":"ACCEPTED");
        var mine=mapper.ownApplication(id,viewer.get("id"));
        job.put("application_status",mine.isEmpty()?null:mine.get(0).get("status"));
        job.put("applied",!mine.isEmpty()&&Set.of("ACTIVE","PENDING").contains(mine.get(0).get("status")));
        job.put("paid",!mine.isEmpty()&&((Number)mine.get(0).get("paid")).intValue()==1);
        return job;
    }
    public String publish(ApiController.JobInput input,Map<String,Object> user) {
        permission(user,"PUBLISHER","can_publish");
        Job job=new Job(UUID.randomUUID().toString(),user.get("id").toString(),input.title().trim(),input.category().trim(),input.requiredCount(),input.description().trim(),input.requirements().trim(),input.location().trim(),input.pay(),"TOTAL",input.startsAt(),input.durationMinutes(),input.recruitmentMode()==null?"DIRECT":input.recruitmentMode());
        mapper.insertJob(job);
        return job.id();
    }
    @Transactional
    public void apply(String id,Map<String,Object> user) {
        apply(id,user,null);
    }
    @Transactional
    public void apply(String id,Map<String,Object> user,String resume) {
        permission(user,"STUDENT","can_accept");
        var rows=mapper.enrollment(id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在");
        var job=rows.get(0);
        boolean screening="SCREENING".equals(job.get("recruitment_mode"));
        if(screening && (resume==null || resume.isBlank() || resume.length()>8000))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写8000字以内的在线简历");
        // Pending resumes do not occupy a seat. Serialize admission with cancellation/acceptance.
        if(screening)job=lockedJob(id);
        if(!"OPEN".equals(job.get("status")))conflict("活动已取消，不能报名");
        if(job.get("starts_at") instanceof java.sql.Timestamp start && !start.toLocalDateTime().isAfter(java.time.LocalDateTime.now()))conflict("兼职已开始，不能继续加入");
        if(screening){
            if(((Number)job.get("remaining_count")).intValue()==0)conflict("名额已满，请选择其他兼职");
        }else if(mapper.reserveSeat(id,"DIRECT")!=1){
            conflict("名额已满、活动已取消或已开始，请刷新后选择其他兼职");
        }
        String applicationId=UUID.randomUUID().toString();
        try {
            mapper.insertApplication(applicationId,id,user.get("id"),screening?"PENDING":"ACTIVE",screening?crypto.encrypt(resume.trim()):null);
        } catch(DuplicateKeyException e) {
            // Propagate a runtime exception so Spring rolls back the seat reservation as well.
            throw new ResponseStatusException(HttpStatus.CONFLICT,"该兼职已有你的报名记录；退出或未录取后不支持再次报名");
        }
        notifications.send(job.get("publisher_id").toString(),"joined:"+applicationId,"JOB_JOINED",screening?"收到新的岗位简历":"有同学参加兼职",ProfileService.displayName(user)+(screening?" 已提交简历报名「":" 已参加「")+job.get("title")+"」。","JOB",id);
    }
    @Transactional
    public void decide(String jobId,String applicationId,Map<String,Object> user,boolean accepted) {
        var rows=mapper.enrollment(jobId);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在");
        var job=rows.get(0);
        if(!"PUBLISHER".equals(user.get("role")) || !user.get("id").equals(job.get("publisher_id")))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在或无权筛选");
        permission(user,"PUBLISHER","can_publish");
        if(!accepted)job=lockedJob(jobId);
        if(!"SCREENING".equals(job.get("recruitment_mode")))conflict("该岗位无需简历筛选");
        if(!"OPEN".equals(job.get("status")))conflict("活动已取消，不能筛选");
        beforeStart(job);
        var apps=mapper.decisionApplication(applicationId,jobId);
        if(apps.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"报名记录不存在");
        var app=apps.get(0);
        String status=accepted?"ACTIVE":"REJECTED";
        if(status.equals(app.get("status")))return;
        if(!"PENDING".equals(app.get("status")))conflict("该报名已处理或已退出，请刷新");
        if(accepted && mapper.reserveSeat(jobId,"SCREENING")!=1)conflict("录取名额已满、活动已取消或已开始，请刷新");
        // Compare-and-set protects against concurrent decisions and withdrawals for one application.
        if(mapper.reviewApplication(status,applicationId)!=1)conflict("该报名已处理或已退出，请刷新");
        notifications.send(app.get("student_id").toString(),"screened:"+applicationId,accepted?"JOB_ACCEPTED":"JOB_REJECTED",accepted?"岗位报名已录取":"岗位报名未录取","「"+job.get("title")+"」的简历筛选结果："+(accepted?"你已被录取，请按时参加。":"本次未被录取，可以浏览其他岗位。"),"JOB",jobId);
    }
    @Transactional
    public void withdraw(String id,Map<String,Object> user){
        if(!"STUDENT".equals(user.get("role")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅学生可以退出报名");
        var job=lockedJob(id);
        var apps=mapper.lockOwnApplication(id,user.get("id"));
        if(apps.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"你尚未报名此兼职");
        var app=apps.get(0);
        if("WITHDRAWN".equals(app.get("status")))return;
        if("REJECTED".equals(app.get("status")))conflict("该报名未录取，无需退出");
        if(!"OPEN".equals(job.get("status")))conflict("活动已取消，无需退出");
        beforeStart(job);
        if(mapper.paymentCount(app.get("id"))>0)conflict("已结算的报名不能退出");
        if(mapper.withdrawApplication(app.get("id"))!=1)conflict("报名状态已变更，请刷新");
        if("ACTIVE".equals(app.get("status")) && mapper.releaseSeat(id)!=1)conflict("名额状态已变更，请刷新");
        boolean pending="PENDING".equals(app.get("status"));
        notifications.send(job.get("publisher_id").toString(),"withdrawn:"+app.get("id"),"JOB_WITHDRAWN",pending?"有同学撤回报名":"有同学退出兼职",ProfileService.displayName(user)+(pending?" 已撤回「":" 已退出「")+job.get("title")+(pending?"」的简历报名。":"」，名额已释放。"),"JOB",id);
    }
    @Transactional
    public void cancel(String id,Map<String,Object> user,String reason){
        var job=lockedJob(id);
        if(!user.get("id").equals(job.get("publisher_id")))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"岗位不存在或无权取消");
        if("CANCELLED".equals(job.get("status")))return;
        beforeStart(job);
        if(mapper.jobPaymentCount(id)>0)conflict("已有报酬结算的活动不能直接取消");
        var people=mapper.cancelRecipients(id);
        mapper.cancelJob(reason.trim(),id);
        for(var person:people)notifications.send(person.get("student_id").toString(),"cancelled:"+id,"JOB_CANCELLED","你报名的活动已取消","「"+job.get("title")+"」已取消。原因："+reason.trim(),"JOB",id);
    }
    private Map<String,Object> lockedJob(String id){
        var rows=mapper.lockJob(id);
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
