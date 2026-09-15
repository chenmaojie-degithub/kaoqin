package com.example.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.attendance.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {
    @Delete("DELETE FROM attendance_record WHERE student_id=#{studentId} AND course_id=#{courseId} AND attendance_date=#{attendanceDate}")
    int hardDeleteException(@Param("studentId") Long studentId,
                            @Param("courseId") Long courseId,
                            @Param("attendanceDate") java.time.LocalDate attendanceDate);
}
