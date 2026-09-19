package com.example.attendance.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.attendance.entity.AttendanceTask;
import org.apache.ibatis.annotations.Select;
public interface AttendanceTaskMapper extends BaseMapper<AttendanceTask> {
    @Select("SELECT * FROM attendance_task WHERE id=#{id} AND deleted=0 FOR UPDATE") AttendanceTask lockById(Long id);
}
