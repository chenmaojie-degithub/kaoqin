package com.example.attendance.service;
import com.baomidou.mybatisplus.core.metadata.IPage; import com.example.attendance.dto.TeacherSaveRequest; import com.example.attendance.entity.Teacher;
public interface TeacherService { IPage<Teacher> page(long page,long size,String keyword); Teacher create(TeacherSaveRequest r); Teacher update(Long id,TeacherSaveRequest r); void delete(Long id); }

