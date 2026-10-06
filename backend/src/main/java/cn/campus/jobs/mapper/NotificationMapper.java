package cn.campus.jobs.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {
    int insert(@Param("id") Object id, @Param("recipientId") Object recipientId, @Param("eventKey") Object eventKey, @Param("kind") Object kind, @Param("title") Object title, @Param("content") Object content, @Param("targetType") Object targetType, @Param("targetId") Object targetId);
    Long unreadCount(@Param("recipientId") Object recipientId);
    List<Map<String,Object>> list(@Param("recipientId") Object recipientId, @Param("offset") int offset, @Param("unreadOnly") boolean unreadOnly);
    Long count(@Param("recipientId") Object recipientId, @Param("unreadOnly") boolean unreadOnly);
    Integer ownedCount(@Param("id") Object id, @Param("recipientId") Object recipientId);
    int markRead(@Param("id") Object id, @Param("recipientId") Object recipientId);
    int markAllRead(@Param("recipientId") Object recipientId);
}
