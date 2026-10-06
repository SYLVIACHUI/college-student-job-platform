package cn.campus.jobs.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CompanyMapper {
    List<Map<String,Object>> profile(@Param("publisherId") Object publisherId);
    List<Map<String,Object>> photos(@Param("publisherId") Object publisherId);
    Integer profileCount(@Param("publisherId") Object publisherId);
    int insertProfile(@Param("publisherId") Object publisherId, @Param("introduction") Object introduction);
    int updateProfile(@Param("introduction") Object introduction, @Param("publisherId") Object publisherId);
    Integer photoCount(@Param("publisherId") Object publisherId);
    int insertPhoto(@Param("id") Object id, @Param("publisherId") Object publisherId, @Param("image") byte[] image);
    int deletePhoto(@Param("id") Object id, @Param("publisherId") Object publisherId);
    List<byte[]> photo(@Param("publisherId") Object publisherId, @Param("id") Object id);
}
