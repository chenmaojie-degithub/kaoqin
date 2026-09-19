package com.example.attendance.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.attendance.common.ApiResponse;
import com.example.attendance.config.SemesterProperties;
import com.example.attendance.dto.ChangePasswordRequest;
import com.example.attendance.entity.*;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.mapper.*;
import com.example.attendance.security.SecurityUtils;
import com.example.attendance.service.AttendanceService;
import com.example.attendance.service.SemesterCalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/student-portal")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentPortalController {
    private final StudentMapper students;
    private final UserMapper users;
    private final ClassInfoMapper classes;
    private final CourseClassMapper assignments;
    private final CourseScheduleMapper schedules;
    private final CourseMapper courses;
    private final TeacherMapper teachers;
    private final AttendanceTaskMapper tasks;
    private final AttendanceRecordMapper records;
    private final LeaveRequestMapper leaves;
    private final AttendanceService attendanceService;
    private final PasswordEncoder passwordEncoder;
    private final SemesterProperties semesterProperties;
    private final SemesterCalendarService semesterCalendarService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Student student = currentStudent(); LocalDate today = LocalDate.now();
        List<Map<String, Object>> all = attendanceService.records(null, student.getId(), null, null);
        List<Map<String, Object>> todayRows = all.stream().filter(row -> today.toString().equals(String.valueOf(row.get("attendanceDate")))).toList();
        List<Map<String, Object>> month = all.stream().filter(row -> String.valueOf(row.get("attendanceDate")).startsWith(YearMonth.now().toString())).toList();
        long attended = month.stream().filter(row -> Set.of("NORMAL", "LATE", "EARLY_LEAVE").contains(row.get("status"))).count();
        String todayStatus = todayRows.isEmpty() ? "NO_TASK" : todayRows.stream().map(row -> String.valueOf(row.get("status"))).filter(status -> !"NORMAL".equals(status)).findFirst().orElseGet(() ->
                todayRows.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("checkedIn"))) ? "NORMAL" :
                        todayRows.stream().anyMatch(row -> "OPEN".equals(row.get("taskStatus"))) ? "PENDING" : "NORMAL");
        long pendingLeaves = leaves.selectCount(Wrappers.<LeaveRequest>lambdaQuery().eq(LeaveRequest::getStudentId, student.getId()).eq(LeaveRequest::getStatus, "PENDING"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayStatus", todayStatus); result.put("todayCourses", todayRows.size());
        result.put("recentAttendance", all.stream().limit(5).toList());
        result.put("monthlyRate", month.isEmpty() ? 100 : Math.round(attended * 1000.0 / month.size()) / 10.0);
        result.put("pendingLeaves", pendingLeaves); result.put("latestNotifications", notificationRows(student, 5));
        return ApiResponse.ok(result);
    }

    @GetMapping("/today-schedule")
    public ApiResponse<List<Map<String, Object>>> todaySchedule() {
        Student student = currentStudent(); LocalDate today = LocalDate.now(); int weekday = today.getDayOfWeek().getValue();
        if (!"ACTIVE".equals(semesterCalendarService.getCalendar(null).status())) return ApiResponse.ok(List.of());
        List<CourseClass> ccList = assignments.selectList(Wrappers.<CourseClass>lambdaQuery()
                .eq(CourseClass::getClassId, student.getClassId())
                .eq(CourseClass::getAcademicYear, semesterProperties.getAcademicYear())
                .eq(CourseClass::getSemester, semesterProperties.getName()));
        Map<Long, CourseClass> ccMap = new HashMap<>(); ccList.forEach(cc -> ccMap.put(cc.getId(), cc));
        if (ccMap.isEmpty()) return ApiResponse.ok(List.of());
        int currentWeek = semesterCalendarService.getCalendar(null).currentWeek();
        List<CourseSchedule> todaySchedules = schedules.selectList(Wrappers.<CourseSchedule>lambdaQuery().in(CourseSchedule::getCourseClassId, ccMap.keySet()).eq(CourseSchedule::getWeekday, weekday)
                .le(CourseSchedule::getStartWeek, currentWeek).ge(CourseSchedule::getEndWeek, currentWeek).orderByAsc(CourseSchedule::getStartTime));
        Map<Long, AttendanceTask> taskMap = new HashMap<>();
        tasks.selectList(Wrappers.<AttendanceTask>lambdaQuery().in(AttendanceTask::getCourseClassId, ccMap.keySet()).eq(AttendanceTask::getAttendanceDate, today)).forEach(task -> taskMap.put(task.getCourseClassId(), task));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseSchedule schedule : todaySchedules) {
            CourseClass cc = ccMap.get(schedule.getCourseClassId()); Course course = courses.selectById(cc.getCourseId()); Teacher teacher = teachers.selectById(cc.getTeacherId()); AttendanceTask task = taskMap.get(cc.getId());
            Map<String, Object> row = new LinkedHashMap<>(); row.put("courseName", course == null ? "" : course.getName()); row.put("teacherName", teacher == null ? "" : teacher.getName());
            row.put("courseClassId", cc.getId()); row.put("startTime", schedule.getStartTime()); row.put("endTime", schedule.getEndTime()); row.put("location", schedule.getLocation());
            Map<String, Object> session = task == null ? null : attendanceService.studentSession(task.getId());
            row.put("taskId", task == null ? null : task.getId()); row.put("taskStatus", task == null ? "NONE" : task.getStatus());
            row.put("requiresAttendance", session != null && !Boolean.TRUE.equals(session.get("checkedIn")));
            if (session != null) { row.put("sessionId", session.get("sessionId")); row.put("sessionStatus", session.get("status")); row.put("expiresAt", session.get("expiresAt")); row.put("serverTime", session.get("serverTime")); row.put("checkedIn", session.get("checkedIn")); }
            result.add(row);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/notifications")
    public ApiResponse<List<Map<String, Object>>> notifications() { return ApiResponse.ok(notificationRows(currentStudent(), 100)); }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        Student student = currentStudent(); ClassInfo classInfo = classes.selectById(student.getClassId()); User user = users.selectById(student.getUserId());
        Map<String, Object> result = new LinkedHashMap<>(); result.put("name", student.getName()); result.put("studentNo", student.getStudentNo());
        result.put("className", classInfo == null ? "" : classInfo.getName()); result.put("username", user == null ? "" : user.getUsername());
        result.put("gender", student.getGender()); result.put("phone", student.getPhone()); result.put("email", student.getEmail()); result.put("enrollmentDate", student.getEnrollmentDate());
        return ApiResponse.ok(result);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        User user = users.selectById(SecurityUtils.current().id());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) throw BusinessException.conflict("当前密码不正确");
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) throw BusinessException.conflict("新密码不能与当前密码相同");
        user.setPassword(passwordEncoder.encode(request.newPassword())); users.updateById(user); return ApiResponse.ok();
    }

    private Student currentStudent() {
        Student student = students.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId, SecurityUtils.current().id()));
        if (student == null) throw BusinessException.forbidden("当前账号未关联学生"); return student;
    }

    private List<Map<String, Object>> notificationRows(Student student, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LeaveRequest leave : leaves.selectList(Wrappers.<LeaveRequest>lambdaQuery().eq(LeaveRequest::getStudentId, student.getId()).ne(LeaveRequest::getStatus, "PENDING").orderByDesc(LeaveRequest::getApprovedAt).last("LIMIT " + limit))) {
            result.add(notification("LEAVE", "请假" + ("APPROVED".equals(leave.getStatus()) ? "已通过" : "已拒绝"), leave.getApprovalComment() == null ? "请查看申请详情" : leave.getApprovalComment(), leave.getApprovedAt()));
        }
        for (AttendanceRecord record : records.selectList(Wrappers.<AttendanceRecord>lambdaQuery().eq(AttendanceRecord::getStudentId, student.getId()).notIn(AttendanceRecord::getStatus, List.of("LEAVE", "NORMAL")).orderByDesc(AttendanceRecord::getAttendanceDate).last("LIMIT " + limit))) {
            Course course = courses.selectById(record.getCourseId()); result.add(notification("ATTENDANCE", "考勤异常提醒", (course == null ? "课程" : course.getName()) + "：" + statusName(record.getStatus()), record.getUpdatedAt()));
        }
        List<Long> ccIds = assignments.selectList(Wrappers.<CourseClass>lambdaQuery().eq(CourseClass::getClassId, student.getClassId())).stream().map(CourseClass::getId).toList();
        if (!ccIds.isEmpty()) for (AttendanceTask task : tasks.selectList(Wrappers.<AttendanceTask>lambdaQuery().in(AttendanceTask::getCourseClassId, ccIds).orderByDesc(AttendanceTask::getCreatedAt).last("LIMIT " + limit))) {
            CourseClass cc = assignments.selectById(task.getCourseClassId()); Course course = courses.selectById(cc.getCourseId()); result.add(notification("TASK", "考勤通知", (course == null ? "课程" : course.getName()) + "已发布考勤任务", task.getCreatedAt()));
        }
        result.sort((a, b) -> String.valueOf(b.get("time")).compareTo(String.valueOf(a.get("time")))); return result.stream().limit(limit).toList();
    }

    private Map<String, Object> notification(String type, String title, String content, LocalDateTime time) { Map<String, Object> row = new LinkedHashMap<>(); row.put("type", type); row.put("title", title); row.put("content", content); row.put("time", time); return row; }
    private String statusName(String status) { return Map.of("LATE", "迟到", "EARLY_LEAVE", "早退", "ABSENT", "缺勤", "LEAVE", "请假").getOrDefault(status, status); }
}
