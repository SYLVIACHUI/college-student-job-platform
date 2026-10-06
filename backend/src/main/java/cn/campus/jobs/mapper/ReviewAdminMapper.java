package cn.campus.jobs.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReviewAdminMapper {
    List<Map<String,Object>> passwordHash(@Param("username") Object username);
    int insertAdmin(@Param("id") Object id, @Param("username") Object username, @Param("passwordHash") Object passwordHash, @Param("displayName") Object displayName);
    List<Map<String,Object>> account(@Param("username") Object username);
    List<Map<String,Object>> current(@Param("id") Object id);
    Long pendingCount(@Param("role") Object role);
    List<Map<String,Object>> pending(@Param("role") Object role, @Param("offset") int offset);
    List<Map<String,Object>> events(@Param("userId") Object userId);
    int insertAction(@Param("id") Object id, @Param("adminId") Object adminId, @Param("userId") Object userId, @Param("reviewId") Object reviewId, @Param("decision") Object decision, @Param("note") Object note);
    List<Map<String,Object>> history(@Param("offset") int offset);
    Long historyCount();
}
