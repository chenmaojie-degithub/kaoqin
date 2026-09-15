package com.example.attendance.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.attendance.common.ApiResponse;
import com.example.attendance.entity.*;
import com.example.attendance.mapper.*;
import com.example.attendance.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/statistics") @RequiredArgsConstructor
public class StatisticsController {
    private final AttendanceRecordMapper records; private final AttendanceTaskMapper tasks; private final CourseClassMapper assignments;
    private final StudentMapper students; private final TeacherMapper teachers; private final ClassInfoMapper classes; private final LeaveRequestMapper leaves;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        List<Snapshot> list = snapshots(); LocalDate today = LocalDate.now();
        Map<String, Long> todayCounts = list.stream().filter(x -> today.equals(x.date())).collect(Collectors.groupingBy(Snapshot::status, Collectors.counting()));
        Map<String, Long> allCounts = list.stream().collect(Collectors.groupingBy(Snapshot::status, Collectors.counting()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) { LocalDate day = today.minusDays(i); long total = list.stream().filter(x -> day.equals(x.date())).count(); long present = list.stream().filter(x -> day.equals(x.date()) && isPresent(x.status())).count(); trend.add(Map.of("date", day.toString(), "total", total, "present", present)); }
        List<Map<String, Object>> classRates = new ArrayList<>();
        for (ClassInfo classInfo : classes.selectList(null)) { Set<Long> ids = students.selectList(Wrappers.<Student>lambdaQuery().eq(Student::getClassId, classInfo.getId())).stream().map(Student::getId).collect(Collectors.toSet()); List<Snapshot> rows = list.stream().filter(x -> ids.contains(x.studentId())).toList(); if (!rows.isEmpty()) { long present = rows.stream().filter(x -> isPresent(x.status())).count(); classRates.add(Map.of("name", classInfo.getName(), "rate", Math.round(present * 1000.0 / rows.size()) / 10.0)); } }
        long total = list.size(), present = list.stream().filter(x -> isPresent(x.status())).count(); Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", Map.of("normal", todayCounts.getOrDefault("NORMAL", 0L), "late", todayCounts.getOrDefault("LATE", 0L), "leave", todayCounts.getOrDefault("LEAVE", 0L), "absent", todayCounts.getOrDefault("ABSENT", 0L)));
        result.put("trend", trend); result.put("statusDistribution", allCounts); result.put("classRates", classRates); result.put("attendanceRate", total == 0 ? 0 : Math.round(present * 1000.0 / total) / 10.0); return ApiResponse.ok(result);
    }

    private List<Snapshot> snapshots() {
        String role = SecurityUtils.current().role(); List<CourseClass> ccList;
        Student currentStudent = null;
        if ("STUDENT".equals(role)) { currentStudent = students.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId, SecurityUtils.current().id())); if (currentStudent == null) return List.of(); ccList = assignments.selectList(Wrappers.<CourseClass>lambdaQuery().eq(CourseClass::getClassId, currentStudent.getClassId())); }
        else if ("TEACHER".equals(role)) { Teacher teacher = teachers.selectOne(Wrappers.<Teacher>lambdaQuery().eq(Teacher::getUserId, SecurityUtils.current().id())); if (teacher == null) return List.of(); ccList = assignments.selectList(Wrappers.<CourseClass>lambdaQuery().eq(CourseClass::getTeacherId, teacher.getId())); }
        else ccList = assignments.selectList(null);
        if (ccList.isEmpty()) return List.of(); Map<Long, CourseClass> ccMap = new HashMap<>(); ccList.forEach(cc -> ccMap.put(cc.getId(), cc));
        List<AttendanceTask> taskList = tasks.selectList(Wrappers.<AttendanceTask>lambdaQuery().in(AttendanceTask::getCourseClassId, ccMap.keySet()).le(AttendanceTask::getAttendanceDate, LocalDate.now())); if (taskList.isEmpty()) return List.of();
        Map<Long, Map<Long, AttendanceRecord>> exceptions = new HashMap<>(); records.selectList(Wrappers.<AttendanceRecord>lambdaQuery().in(AttendanceRecord::getTaskId, taskList.stream().map(AttendanceTask::getId).toList())).forEach(r -> exceptions.computeIfAbsent(r.getTaskId(), key -> new HashMap<>()).put(r.getStudentId(), r));
        List<Snapshot> result = new ArrayList<>();
        for (AttendanceTask task : taskList) { CourseClass cc = ccMap.get(task.getCourseClassId()); List<Student> roster = currentStudent == null ? students.selectList(Wrappers.<Student>lambdaQuery().eq(Student::getClassId, cc.getClassId()).eq(Student::getStatus, 1)) : List.of(currentStudent); for (Student student : roster) { AttendanceRecord record = exceptions.getOrDefault(task.getId(), Map.of()).get(student.getId()); String status = record == null ? approvedLeave(student.getId(), cc.getCourseId(), task.getAttendanceDate()) : record.getStatus(); result.add(new Snapshot(task.getAttendanceDate(), student.getId(), status)); } }
        return result;
    }

    private String approvedLeave(Long studentId, Long courseId, LocalDate date) { long count = leaves.selectCount(Wrappers.<LeaveRequest>lambdaQuery().eq(LeaveRequest::getStudentId, studentId).eq(LeaveRequest::getStatus, "APPROVED").le(LeaveRequest::getStartTime, date.atTime(LocalTime.MAX)).ge(LeaveRequest::getEndTime, date.atStartOfDay()).and(q -> q.isNull(LeaveRequest::getCourseId).or().eq(LeaveRequest::getCourseId, courseId))); return count > 0 ? "LEAVE" : "NORMAL"; }
    private boolean isPresent(String status) { return Set.of("NORMAL", "LATE", "EARLY_LEAVE").contains(status); }
    private record Snapshot(LocalDate date, Long studentId, String status) {}
}
