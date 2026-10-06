package cn.campus.jobs.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProfileMapper {
    List<byte[]> avatar(@Param("userId") Object userId);
    int updateStudent(@Param("nickname") Object nickname, @Param("birthday") Object birthday, @Param("grade") Object grade, @Param("major") Object major, @Param("bio") Object bio, @Param("id") Object id);
    int updatePublisher(@Param("nickname") Object nickname, @Param("birthday") Object birthday, @Param("bio") Object bio, @Param("id") Object id);
    int deleteAvatar(@Param("userId") Object userId);
    int insertAvatar(@Param("userId") Object userId, @Param("image") byte[] image);
    int setAvatarVersion(@Param("version") Object version, @Param("id") Object id, @Param("table") String table);
    Long historyCount(@Param("userId") Object userId, @Param("publisher") boolean publisher);
    List<Map<String,Object>> publisherHistory(@Param("publisherId") Object publisherId, @Param("offset") int offset);
    List<Map<String,Object>> studentHistory(@Param("studentId") Object studentId, @Param("offset") int offset);
    List<Map<String,Object>> ownedJob(@Param("id") Object id, @Param("publisherId") Object publisherId);
    List<Map<String,Object>> applicants(@Param("jobId") Object jobId, @Param("offset") int offset);
    Integer registeredCount(@Param("jobId") Object jobId);
    Integer acceptedCount(@Param("jobId") Object jobId);
    Long applicationCount(@Param("jobId") Object jobId);
}
