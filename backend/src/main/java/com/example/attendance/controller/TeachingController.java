package com.example.attendance.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.attendance.common.ApiResponse;
import com.example.attendance.config.SemesterProperties;
import com.example.attendance.dto.CourseScheduleRequest;
import com.example.attendance.dto.ScheduleEditMode;
import com.example.attendance.entity.*;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.mapper.*;
import com.example.attendance.security.SecurityUtils;
import com.example.attendance.service.CourseScheduleService;
import com.example.attendance.service.SemesterCalendarService;
import com.example.attendance.vo.SemesterWeekVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/teaching") @RequiredArgsConstructor
public class TeachingController {
    private final CourseClassMapper assignments; private final CourseScheduleMapper schedules;
    private final CourseMapper courses; private final ClassInfoMapper classes; private final TeacherMapper teachers; private final StudentMapper students;
    private final CourseScheduleService scheduleService;
    private final SemesterCalendarService semesterCalendarService;
    private final SemesterProperties semesterProperties;

    @GetMapping("/semester-calendar")
    public ApiResponse<SemesterWeekVO> semesterCalendar(@RequestParam(required = false) Integer week) {
        return ApiResponse.ok(semesterCalendarService.getCalendar(week));
    }

    @GetMapping
    public ApiResponse<List<Map<String,Object>>> list(){
        var query=Wrappers.<CourseClass>lambdaQuery()
                .eq(CourseClass::getAcademicYear, semesterProperties.getAcademicYear())
                .eq(CourseClass::getSemester, semesterProperties.getName())
                .orderByDesc(CourseClass::getId);
        String role=SecurityUtils.current().role();
        if("TEACHER".equals(role)){Teacher t=teachers.selectOne(Wrappers.<Teacher>lambdaQuery().eq(Teacher::getUserId,SecurityUtils.current().id()));if(t==null)return ApiResponse.ok(List.of());query.eq(CourseClass::getTeacherId,t.getId());}
        if("STUDENT".equals(role)){Student s=students.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId,SecurityUtils.current().id()));if(s==null)return ApiResponse.ok(List.of());query.eq(CourseClass::getClassId,s.getClassId());}
        List<Map<String,Object>> result=new ArrayList<>();
        for(CourseClass value:assignments.selectList(query)){Course c=courses.selectById(value.getCourseId());ClassInfo ci=classes.selectById(value.getClassId());Teacher t=teachers.selectById(value.getTeacherId());Map<String,Object> row=new LinkedHashMap<>();row.put("id",value.getId());row.put("courseId",value.getCourseId());row.put("courseName",c==null?"":c.getName());row.put("classId",value.getClassId());row.put("className",ci==null?"":ci.getName());row.put("teacherId",value.getTeacherId());row.put("teacherName",t==null?"":t.getName());row.put("academicYear",value.getAcademicYear());row.put("semester",value.getSemester());row.put("schedules",schedules.selectList(Wrappers.<CourseSchedule>lambdaQuery().eq(CourseSchedule::getCourseClassId,value.getId())));result.add(row);}
        return ApiResponse.ok(result);
    }
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<CourseClass> create(@RequestBody CourseClass value){assignments.insert(value);return ApiResponse.ok(value);}
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<CourseClass> update(@PathVariable Long id,@RequestBody CourseClass value){if(assignments.selectById(id)==null)throw BusinessException.notFound("Assignment not found");value.setId(id);assignments.updateById(value);return ApiResponse.ok(value);}
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<Void> delete(@PathVariable Long id){if(assignments.deleteById(id)==0)throw BusinessException.notFound("Assignment not found");return ApiResponse.ok();}
    @PostMapping("/{id}/schedules") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')") public ApiResponse<CourseSchedule> addSchedule(@PathVariable Long id,@Valid @RequestBody CourseScheduleRequest value){return ApiResponse.ok(scheduleService.create(id,value));}
    @PutMapping("/{courseClassId}/schedules/{scheduleId}") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<CourseSchedule> updateSchedule(@PathVariable Long courseClassId, @PathVariable Long scheduleId,
            @RequestParam(defaultValue = "ALL") ScheduleEditMode mode,
            @RequestParam(required = false) Integer currentWeek,
            @Valid @RequestBody CourseScheduleRequest value) {
        return ApiResponse.ok(scheduleService.update(scheduleId, courseClassId, value, mode, currentWeek));
    }
    @DeleteMapping("/schedules/{id}") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')") public ApiResponse<Void> deleteSchedule(@PathVariable Long id){scheduleService.delete(id);return ApiResponse.ok();}
}
