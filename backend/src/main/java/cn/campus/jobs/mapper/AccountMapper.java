package cn.campus.jobs.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {
    String role(@Param("id") String id);
    int insertIdentity(@Param("id") String id, @Param("role") String role);
    int createWallet(@Param("userId") Object userId);
    int touchLogin(@Param("userId") Object userId, @Param("table") String table);
    Long publishedCount(@Param("publisherId") Object publisherId);
    int seedPublisher(@Param("organization") Object organization, @Param("surname") Object surname, @Param("nameCipher") Object nameCipher, @Param("identityCipher") Object identityCipher, @Param("nickname") Object nickname, @Param("reviewId") Object reviewId, @Param("note") Object note, @Param("id") Object id);
    int seedStudent(@Param("school") Object school, @Param("nameCipher") Object nameCipher, @Param("studentNumberCipher") Object studentNumberCipher, @Param("nickname") Object nickname, @Param("grade") Object grade, @Param("major") Object major, @Param("reviewId") Object reviewId, @Param("note") Object note, @Param("id") Object id);
    int seedVerification(@Param("id") Object id, @Param("userId") Object userId, @Param("note") Object note);
}
