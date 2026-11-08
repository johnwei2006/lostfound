package com.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lostfound.entity.Found;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FoundMapper extends BaseMapper<Found> {

    @Update("UPDATE found SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementView(@Param("id") Long id);
}
