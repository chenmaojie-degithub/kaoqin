package com.example.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.attendance.entity.ClassInfo;
import org.apache.ibatis.annotations.Select;

public interface ClassInfoMapper extends BaseMapper<ClassInfo> {
    @Select("SELECT id FROM class_info WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Long lockActiveById(Long id);
}
