package cn.campus.jobs.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobMapper {
    List<Map<String,Object>> companyJobs(@Param("publisherId") Object publisherId, @Param("now") Object now, @Param("offset") int offset);
    Long companyJobCount(@Param("publisherId") Object publisherId, @Param("now") Object now);
    List<Map<String,Object>> listJobs(@Param("viewerId") Object viewerId, @Param("publisher") boolean publisher);
    List<Map<String,Object>> detail(@Param("id") Object id);
    List<Map<String,Object>> participants(@Param("jobId") Object jobId);
    Integer registeredCount(@Param("jobId") Object jobId);
    List<Map<String,Object>> ownApplication(@Param("jobId") Object jobId, @Param("studentId") Object studentId);
    int insertJob(@Param("job") cn.campus.jobs.entity.Job job);
    List<Map<String,Object>> enrollment(@Param("id") String id);
    int reserveSeat(@Param("id") String id, @Param("mode") String mode);
    int releaseSeat(@Param("id") String id);
    int insertApplication(@Param("id") Object id, @Param("jobId") Object jobId, @Param("studentId") Object studentId, @Param("status") Object status, @Param("resumeCipher") Object resumeCipher);
    List<Map<String,Object>> decisionApplication(@Param("id") String id, @Param("jobId") String jobId);
    int reviewApplication(@Param("status") Object status, @Param("id") Object id);
    List<Map<String,Object>> lockOwnApplication(@Param("jobId") Object jobId, @Param("studentId") Object studentId);
    Integer paymentCount(@Param("applicationId") Object applicationId);
    int withdrawApplication(@Param("id") Object id);
    Integer jobPaymentCount(@Param("jobId") Object jobId);
    List<Map<String,Object>> cancelRecipients(@Param("jobId") Object jobId);
    int cancelJob(@Param("reason") Object reason, @Param("id") Object id);
    List<Map<String,Object>> lockJob(@Param("id") Object id);
}
