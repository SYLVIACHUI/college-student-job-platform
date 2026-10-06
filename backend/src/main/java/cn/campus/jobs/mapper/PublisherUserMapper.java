package cn.campus.jobs.mapper;

import cn.campus.jobs.entity.PublisherUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PublisherUserMapper {
    PublisherUser findById(@Param("id") String id, @Param("lock") boolean lock);
    PublisherUser findByAccount(@Param("hash") String hash);
    int insert(@Param("id") String id, @Param("hash") String hash,
        @Param("phoneCipher") String phoneCipher, @Param("passwordHash") String passwordHash);
}
