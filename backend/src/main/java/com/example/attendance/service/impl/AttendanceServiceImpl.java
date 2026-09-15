package com.example.attendance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.attendance.dto.AttendanceRecordItem;
import com.example.attendance.dto.AttendanceTaskCreateRequest;
import com.example.attendance.entity.*;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.mapper.*;
import com.example.attendance.security.SecurityUtils;
import com.example.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {
    private static final int EDIT_MINUTES_AFTER_CLOSE = 20;
    private final AttendanceTaskMapper tasks;
    private final AttendanceRecordMapper records;
    private final CourseClassMapper courseClasses;
    private final CourseMapper courses;
    private final StudentMapper students;
    private final TeacherMapper teachers;
    private final LeaveRequestMapper leaves;

    private CourseClass allowed(Long ccId) {
        CourseClass cc = courseClasses.selectById(ccId);
        if (cc == null) throw BusinessException.notFound("教学班不存在");
        if (!SecurityUtils.isAdmin()) {
            Teacher teacher = teachers.selectOne(Wrappers.<Teacher>lambdaQuery().eq(Teacher::getUserId, SecurityUtils.current().id()));
            if (teacher == null || !teacher.getId().equals(cc.getTeacherId())) throw BusinessException.forbidden("只能操作本人负责的教学班");
        }
        return cc;
    }

    @Override @Transactional
    public AttendanceTask createTask(AttendanceTaskCreateRequest request) {
        allowed(request.courseClassId());
        if (tasks.selectCount(Wrappers.<AttendanceTask>lambdaQuery().eq(AttendanceTask::getCourseClassId, request.courseClassId()).eq(AttendanceTask::getAttendanceDate, request.attendanceDate())) > 0)
            throw BusinessException.conflict("该教学班当天已创建考勤任务");
        AttendanceTask task = new AttendanceTask();
        task.setCourseClassId(request.courseClassId()); task.setAttendanceDate(request.attendanceDate());
        task.setStatus("OPEN"); task.setRemark(request.remark()); task.setCreatedBy(SecurityUtils.current().id());
        tasks.insert(task); return task;
    }

    @Override @Transactional
    public AttendanceTask changeTaskStatus(Long id, String status) {
        if (!Set.of("OPEN", "CLOSED").contains(status)) throw BusinessException.conflict("考勤任务状态无效");
        AttendanceTask task = requiredTask(id); allowed(task.getCourseClassId());
        if (Objects.equals(task.getStatus(), status)) return task;
        if ("CLOSED".equals(task.getStatus())) assertEditable(task);
        task.setStatus(status); task.setClosedAt("CLOSED".equals(status) ? LocalDateTime.now() : null);
        tasks.updateById(task); return task;
    }

    @Override
    public List<Map<String, Object>> taskStudents(Long taskId) {
        AttendanceTask task = requiredTask(taskId); CourseClass cc = allowed(task.getCourseClassId());
        List<Student> roster = students.selectList(Wrappers.<Student>lambdaQuery().eq(Student::getClassId, cc.getClassId()).eq(Student::getStatus, 1).orderByAsc(Student::getStudentNo));
        Map<Long, AttendanceRecord> byStudent = new HashMap<>();
        records.selectList(Wrappers.<AttendanceRecord>lambdaQuery().eq(AttendanceRecord::getTaskId, taskId)).forEach(r -> byStudent.put(r.getStudentId(), r));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Student student : roster) {
            AttendanceRecord record = byStudent.get(student.getId()); Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", student.getId()); row.put("studentNo", student.getStudentNo()); row.put("name", student.getName());
            row.put("status", record == null ? approvedLeave(student.getId(), cc.getCourseId(), task.getAttendanceDate()) : record.getStatus());
            row.put("remark", record == null ? null : record.getRemark()); row.put("editable", editable(task)); row.put("editableUntil", editableUntil(task));
            result.add(row);
        }
        return result;
    }

    @Override @Transactional
    public void saveRecords(Long taskId, List<AttendanceRecordItem> items) {
        AttendanceTask task = requiredTask(taskId); assertEditable(task); CourseClass cc = allowed(task.getCourseClassId());
        Set<Long> roster = new HashSet<>();
        students.selectList(Wrappers.<Student>lambdaQuery().eq(Student::getClassId, cc.getClassId())).forEach(s -> roster.add(s.getId()));
        for (AttendanceRecordItem item : items) {
            if (!roster.contains(item.studentId())) throw BusinessException.conflict("学生不属于该教学班");
            AttendanceRecord existing = records.selectOne(Wrappers.<AttendanceRecord>lambdaQuery().eq(AttendanceRecord::getStudentId, item.studentId()).eq(AttendanceRecord::getCourseId, cc.getCourseId()).eq(AttendanceRecord::getAttendanceDate, task.getAttendanceDate()));
            if ("NORMAL".equals(item.status())) { records.hardDeleteException(item.studentId(), cc.getCourseId(), task.getAttendanceDate()); continue; }
            AttendanceRecord record = existing == null ? new AttendanceRecord() : existing;
            if (existing == null) { record.setTaskId(taskId); record.setStudentId(item.studentId()); record.setCourseId(cc.getCourseId()); record.setAttendanceDate(task.getAttendanceDate()); record.setRecordedBy(SecurityUtils.current().id()); }
            record.setStatus(item.status()); record.setRemark(item.remark());
            if (record.getId() == null) records.insert(record); else records.updateById(record);
        }
    }

    @Override
    public List<Map<String, Object>> records(Long taskId, Long studentId, Long courseId, LocalDate date) {
        if ("STUDENT".equals(SecurityUtils.current().role())) return studentRecords(taskId, courseId, date);
        var query = Wrappers.<AttendanceRecord>lambdaQuery().eq(taskId != null, AttendanceRecord::getTaskId, taskId).eq(courseId != null, AttendanceRecord::getCourseId, courseId).eq(date != null, AttendanceRecord::getAttendanceDate, date).eq(studentId != null, AttendanceRecord::getStudentId, studentId);
        if ("TEACHER".equals(SecurityUtils.current().role())) {
            Teacher teacher = teachers.selectOne(Wrappers.<Teacher>lambdaQuery().eq(Teacher::getUserId, SecurityUtils.current().id()));
            List<Long> ccIds = teacher == null ? List.of() : courseClasses.selectList(Wrappers.<CourseClass>lambdaQuery().eq(CourseClass::getTeacherId, teacher.getId())).stream().map(CourseClass::getId).toList();
            List<Long> taskIds = ccIds.isEmpty() ? List.of() : tasks.selectList(Wrappers.<AttendanceTask>lambdaQuery().in(AttendanceTask::getCourseClassId, ccIds)).stream().map(AttendanceTask::getId).toList();
            if (taskIds.isEmpty()) return List.of(); query.in(AttendanceRecord::getTaskId, taskIds);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (AttendanceRecord record : records.selectList(query.orderByDesc(AttendanceRecord::getAttendanceDate))) result.add(recordRow(record, students.selectById(record.getStudentId())));
        return result;
    }

    private List<Map<String, Object>> studentRecords(Long taskId, Long courseId, LocalDate date) {
        Student me = students.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId, SecurityUtils.current().id()));
        if (me == null) return List.of();
        List<CourseClass> assignmentList = courseClasses.selectList(Wrappers.<CourseClass>lambdaQuery().eq(CourseClass::getClassId, me.getClassId()).eq(courseId != null, CourseClass::getCourseId, courseId));
        Map<Long, CourseClass> byId = new HashMap<>(); assignmentList.forEach(cc -> byId.put(cc.getId(), cc)); if (byId.isEmpty()) return List.of();
        var taskQuery = Wrappers.<AttendanceTask>lambdaQuery().in(AttendanceTask::getCourseClassId, byId.keySet()).eq(taskId != null, AttendanceTask::getId, taskId).eq(date != null, AttendanceTask::getAttendanceDate, date).le(date == null, AttendanceTask::getAttendanceDate, LocalDate.now()).orderByDesc(AttendanceTask::getAttendanceDate).orderByDesc(AttendanceTask::getId);
        List<AttendanceTask> taskList = tasks.selectList(taskQuery); Map<Long, AttendanceRecord> exceptions = new HashMap<>();
        if (!taskList.isEmpty()) records.selectList(Wrappers.<AttendanceRecord>lambdaQuery().eq(AttendanceRecord::getStudentId, me.getId()).in(AttendanceRecord::getTaskId, taskList.stream().map(AttendanceTask::getId).toList())).forEach(r -> exceptions.put(r.getTaskId(), r));
        List<Map<String, Object>> result = new ArrayList<>();
        for (AttendanceTask task : taskList) {
            CourseClass cc = byId.get(task.getCourseClassId()); AttendanceRecord exception = exceptions.get(task.getId());
            String status = exception == null ? approvedLeave(me.getId(), cc.getCourseId(), task.getAttendanceDate()) : exception.getStatus();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", exception == null ? null : exception.getId()); row.put("taskId", task.getId()); row.put("studentId", me.getId());
            row.put("studentNo", me.getStudentNo()); row.put("studentName", me.getName()); row.put("courseId", cc.getCourseId());
            Course course = courses.selectById(cc.getCourseId()); row.put("courseName", course == null ? "" : course.getName());
            row.put("attendanceDate", task.getAttendanceDate()); row.put("status", status); row.put("taskStatus", task.getStatus()); row.put("remark", exception == null ? null : exception.getRemark()); result.add(row);
        }
        return result;
    }

    private Map<String, Object> recordRow(AttendanceRecord record, Student student) {
        Map<String, Object> row = new LinkedHashMap<>(); Course course = courses.selectById(record.getCourseId());
        row.put("id", record.getId()); row.put("taskId", record.getTaskId()); row.put("studentId", record.getStudentId());
        row.put("studentNo", student == null ? "" : student.getStudentNo()); row.put("studentName", student == null ? "" : student.getName());
        row.put("courseId", record.getCourseId()); row.put("courseName", course == null ? "" : course.getName()); row.put("attendanceDate", record.getAttendanceDate());
        row.put("status", record.getStatus()); row.put("remark", record.getRemark()); return row;
    }

    private AttendanceTask requiredTask(Long taskId) { AttendanceTask task = tasks.selectById(taskId); if (task == null) throw BusinessException.notFound("考勤任务不存在"); return task; }
    private String approvedLeave(Long studentId, Long courseId, LocalDate date) {
        long count = leaves.selectCount(Wrappers.<LeaveRequest>lambdaQuery().eq(LeaveRequest::getStudentId, studentId).eq(LeaveRequest::getStatus, "APPROVED").le(LeaveRequest::getStartTime, date.atTime(LocalTime.MAX)).ge(LeaveRequest::getEndTime, date.atStartOfDay()).and(q -> q.isNull(LeaveRequest::getCourseId).or().eq(LeaveRequest::getCourseId, courseId)));
        return count > 0 ? "LEAVE" : "NORMAL";
    }
    private void assertEditable(AttendanceTask task) { if (!editable(task)) throw BusinessException.conflict("考勤结束已超过20分钟，记录不可修改"); }
    private boolean editable(AttendanceTask task) { if ("OPEN".equals(task.getStatus())) return true; LocalDateTime at = task.getClosedAt() == null ? task.getUpdatedAt() : task.getClosedAt(); return at != null && !LocalDateTime.now().isAfter(at.plusMinutes(EDIT_MINUTES_AFTER_CLOSE)); }
    private LocalDateTime editableUntil(AttendanceTask task) { if ("OPEN".equals(task.getStatus())) return null; LocalDateTime at = task.getClosedAt() == null ? task.getUpdatedAt() : task.getClosedAt(); return at == null ? null : at.plusMinutes(EDIT_MINUTES_AFTER_CLOSE); }
}
