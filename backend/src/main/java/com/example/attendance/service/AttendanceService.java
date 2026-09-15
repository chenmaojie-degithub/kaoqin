package com.example.attendance.service;
import com.example.attendance.dto.*; import com.example.attendance.entity.*; import java.time.LocalDate; import java.util.*;
public interface AttendanceService { AttendanceTask createTask(AttendanceTaskCreateRequest r); AttendanceTask changeTaskStatus(Long id,String status); List<Map<String,Object>> taskStudents(Long taskId); void saveRecords(Long taskId,List<AttendanceRecordItem> records); List<Map<String,Object>> records(Long taskId,Long studentId,Long courseId,LocalDate date); }
