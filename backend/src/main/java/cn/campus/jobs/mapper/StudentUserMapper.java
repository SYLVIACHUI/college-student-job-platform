package cn.campus.jobs.mapper;

import cn.campus.jobs.entity.StudentUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StudentUserMapper {
    StudentUser findById(@Param("id") String id, @Param("lock") boolean lock);
    StudentUser findByAccount(@Param("hash") String hash);
    int insert(@Param("id") String id, @Param("hash") String hash,
        @Param("phoneCipher") String phoneCipher, @Param("passwordHash") String passwordHash);
}
