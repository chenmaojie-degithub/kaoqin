package com.example.attendance.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.attendance.common.ApiResponse;
import com.example.attendance.dto.*;
import com.example.attendance.entity.*;
import com.example.attendance.mapper.*;
import com.example.attendance.security.SecurityUtils;
import com.example.attendance.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate; import java.util.*;

@RestController @RequestMapping("/attendance") @RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService service; private final AttendanceTaskMapper tasks; private final CourseClassMapper assignments; private final TeacherMapper teachers; private final StudentMapper students;
    @GetMapping("/tasks") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<List<AttendanceTask>> tasks(@RequestParam(required=false)Long courseClassId,@RequestParam(required=false)LocalDate date){var q=Wrappers.<AttendanceTask>lambdaQuery().eq(courseClassId!=null,AttendanceTask::getCourseClassId,courseClassId).eq(date!=null,AttendanceTask::getAttendanceDate,date).orderByDesc(AttendanceTask::getAttendanceDate);if(!SecurityUtils.isAdmin()){Teacher t=teachers.selectOne(Wrappers.<Teacher>lambdaQuery().eq(Teacher::getUserId,SecurityUtils.current().id()));List<Long> ids=t==null?List.of():assignments.selectList(Wrappers.<CourseClass>lambdaQuery().eq(CourseClass::getTeacherId,t.getId())).stream().map(CourseClass::getId).toList();if(ids.isEmpty())return ApiResponse.ok(List.of());q.in(AttendanceTask::getCourseClassId,ids);}return ApiResponse.ok(tasks.selectList(q));}
    @PostMapping("/tasks") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')") public ApiResponse<AttendanceTask> create(@Valid @RequestBody AttendanceTaskCreateRequest r){return ApiResponse.ok(service.createTask(r));}
    @PutMapping("/tasks/{id}/status") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')") public ApiResponse<AttendanceTask> status(@PathVariable Long id,@RequestParam String status){return ApiResponse.ok(service.changeTaskStatus(id,status));}
    @GetMapping("/tasks/{id}/students") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')") public ApiResponse<List<Map<String,Object>>> roster(@PathVariable Long id){return ApiResponse.ok(service.taskStudents(id));}
    @PutMapping("/tasks/{id}/records") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')") public ApiResponse<Void> save(@PathVariable Long id,@Valid @RequestBody List<AttendanceRecordItem> records){service.saveRecords(id,records);return ApiResponse.ok();}
    @GetMapping("/records") public ApiResponse<List<Map<String,Object>>> records(@RequestParam(required=false)Long taskId,@RequestParam(required=false)Long studentId,@RequestParam(required=false)Long courseId,@RequestParam(required=false)LocalDate date){if("STUDENT".equals(SecurityUtils.current().role())){Student s=students.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId,SecurityUtils.current().id()));studentId=s==null?-1L:s.getId();}return ApiResponse.ok(service.records(taskId,studentId,courseId,date));}
}
