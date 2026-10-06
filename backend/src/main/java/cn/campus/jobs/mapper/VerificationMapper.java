package cn.campus.jobs.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VerificationMapper {
    List<Map<String,Object>> events(@Param("userId") Object userId);
    int submitPublisher(@Param("organization") Object organization, @Param("surname") Object surname, @Param("nameCipher") Object nameCipher, @Param("identityCipher") Object identityCipher, @Param("id") Object id);
    int submitStudent(@Param("school") Object school, @Param("nameCipher") Object nameCipher, @Param("studentNumberCipher") Object studentNumberCipher, @Param("id") Object id);
    int markPending(@Param("email") Object email, @Param("note") Object note, @Param("reviewId") Object reviewId, @Param("id") Object id, @Param("table") String table);
    int insertEvent(@Param("id") Object id, @Param("userId") Object userId, @Param("status") Object status, @Param("note") Object note);
    int applyDecision(@Param("status") Object status, @Param("note") Object note, @Param("permission") Object permission, @Param("id") Object id, @Param("table") String table);
}
