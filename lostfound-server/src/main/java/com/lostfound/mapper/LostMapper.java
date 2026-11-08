package com.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lostfound.entity.Lost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LostMapper extends BaseMapper<Lost> {

    @Update("UPDATE lost SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementView(@Param("id") Long id);
}
