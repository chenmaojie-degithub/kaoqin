package com.example.attendance.service;
import com.baomidou.mybatisplus.core.metadata.IPage; import com.example.attendance.dto.StudentSaveRequest; import com.example.attendance.entity.Student;
public interface StudentService { IPage<Student> page(long page,long size,String name,String studentNo,Long classId); Student create(StudentSaveRequest r); Student update(Long id,StudentSaveRequest r); void delete(Long id); }

