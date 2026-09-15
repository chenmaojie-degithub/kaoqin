package com.example.attendance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.attendance.dto.LeaveApprovalRequest;
import com.example.attendance.dto.LeaveCreateRequest;
import com.example.attendance.entity.AttendanceRecord;
import com.example.attendance.entity.AttendanceTask;
import com.example.attendance.entity.CourseClass;
import com.example.attendance.entity.LeaveRequest;
import com.example.attendance.entity.Student;
import com.example.attendance.entity.Teacher;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.mapper.AttendanceRecordMapper;
import com.example.attendance.mapper.AttendanceTaskMapper;
import com.example.attendance.mapper.CourseClassMapper;
import com.example.attendance.mapper.LeaveRequestMapper;
import com.example.attendance.mapper.StudentMapper;
import com.example.attendance.mapper.TeacherMapper;
import com.example.attendance.security.SecurityUtils;
import com.example.attendance.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeaveServiceImpl implements LeaveService {
    private final LeaveRequestMapper leaves;
    private final StudentMapper students;
    private final TeacherMapper teachers;
    private final CourseClassMapper courseClasses;
    private final AttendanceTaskMapper tasks;
    private final AttendanceRecordMapper records;

    private Student currentStudent() {
        Student student = students.selectOne(Wrappers.<Student>lambdaQuery()
                .eq(Student::getUserId, SecurityUtils.current().id()));
        if (student == null) throw BusinessException.forbidden("当前账号未关联学生");
        return student;
    }

    @Override
    public LeaveRequest create(LeaveCreateRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw BusinessException.conflict("结束时间必须晚于开始时间");
        }
        LeaveRequest leave = new LeaveRequest();
        leave.setStudentId(currentStudent().getId());
        leave.setCourseId(request.courseId());
        leave.setStartTime(request.startTime());
        leave.setEndTime(request.endTime());
        leave.setReason(request.reason());
        leave.setStatus("PENDING");
        leaves.insert(leave);
        return leave;
    }

    @Override
    public List<LeaveRequest> list(String status) {
        var query = Wrappers.<LeaveRequest>lambdaQuery()
                .eq(status != null && !status.isBlank(), LeaveRequest::getStatus, status)
                .orderByDesc(LeaveRequest::getId);
        if ("STUDENT".equals(SecurityUtils.current().role())) {
            query.eq(LeaveRequest::getStudentId, currentStudent().getId());
        } else if ("TEACHER".equals(SecurityUtils.current().role())) {
            Teacher teacher = teachers.selectOne(Wrappers.<Teacher>lambdaQuery()
                    .eq(Teacher::getUserId, SecurityUtils.current().id()));
            List<Long> classIds = courseClasses.selectList(Wrappers.<CourseClass>lambdaQuery()
                            .eq(CourseClass::getTeacherId, teacher.getId()))
                    .stream().map(CourseClass::getClassId).distinct().toList();
            if (classIds.isEmpty()) return List.of();
            List<Long> studentIds = students.selectList(Wrappers.<Student>lambdaQuery()
                            .in(Student::getClassId, classIds))
                    .stream().map(Student::getId).toList();
            if (studentIds.isEmpty()) return List.of();
            query.in(LeaveRequest::getStudentId, studentIds);
        }
        return leaves.selectList(query);
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        LeaveRequest leave = leaves.selectById(id);
        if (leave == null) throw BusinessException.notFound("请假申请不存在");
        Student student = currentStudent();
        if (!student.getId().equals(leave.getStudentId())) throw BusinessException.forbidden("只能撤销自己的请假申请");
        if (!"PENDING".equals(leave.getStatus())) throw BusinessException.conflict("只有待审批申请可以撤销");
        leaves.deleteById(id);
    }

    @Override
    @Transactional
    public LeaveRequest approve(Long id, LeaveApprovalRequest request) {
        LeaveRequest leave = leaves.selectById(id);
        if (leave == null) throw BusinessException.notFound("请假申请不存在");
        if (!"PENDING".equals(leave.getStatus())) throw BusinessException.conflict("该申请已审批");
        Student student = students.selectById(leave.getStudentId());
        verifyTeacherPermission(leave, student);

        leave.setStatus(request.status());
        leave.setApprovalComment(request.comment());
        leave.setApproverId(SecurityUtils.current().id());
        leave.setApprovedAt(LocalDateTime.now());
        leaves.updateById(leave);
        if ("APPROVED".equals(request.status())) syncAttendanceRecords(leave, student);
        return leave;
    }

    private void verifyTeacherPermission(LeaveRequest leave, Student student) {
        if (!"TEACHER".equals(SecurityUtils.current().role())) return;
        Teacher teacher = teachers.selectOne(Wrappers.<Teacher>lambdaQuery()
                .eq(Teacher::getUserId, SecurityUtils.current().id()));
        long count = courseClasses.selectCount(Wrappers.<CourseClass>lambdaQuery()
                .eq(CourseClass::getTeacherId, teacher.getId())
                .eq(CourseClass::getClassId, student.getClassId())
                .eq(leave.getCourseId() != null, CourseClass::getCourseId, leave.getCourseId()));
        if (count == 0) throw BusinessException.forbidden("只能审批本人教学班的请假");
    }

    private void syncAttendanceRecords(LeaveRequest leave, Student student) {
        List<CourseClass> assignments = courseClasses.selectList(Wrappers.<CourseClass>lambdaQuery()
                .eq(CourseClass::getClassId, student.getClassId())
                .eq(leave.getCourseId() != null, CourseClass::getCourseId, leave.getCourseId()));
        if (assignments.isEmpty()) return;

        List<AttendanceTask> taskList = tasks.selectList(Wrappers.<AttendanceTask>lambdaQuery()
                .in(AttendanceTask::getCourseClassId, assignments.stream().map(CourseClass::getId).toList())
                .between(AttendanceTask::getAttendanceDate,
                        leave.getStartTime().toLocalDate(), leave.getEndTime().toLocalDate()));
        Map<Long, CourseClass> assignmentMap = new HashMap<>();
        assignments.forEach(item -> assignmentMap.put(item.getId(), item));

        // The database unique key is student + course + date. Use exactly the same identity here.
        // A task-based lookup could miss an existing exception record and then cause a duplicate-key failure.
        for (AttendanceTask task : taskList) {
            CourseClass assignment = assignmentMap.get(task.getCourseClassId());
            AttendanceRecord record = records.selectOne(Wrappers.<AttendanceRecord>lambdaQuery()
                    .eq(AttendanceRecord::getStudentId, leave.getStudentId())
                    .eq(AttendanceRecord::getCourseId, assignment.getCourseId())
                    .eq(AttendanceRecord::getAttendanceDate, task.getAttendanceDate()));
            if (record == null) {
                record = new AttendanceRecord();
                record.setTaskId(task.getId());
                record.setStudentId(leave.getStudentId());
                record.setCourseId(assignment.getCourseId());
                record.setAttendanceDate(task.getAttendanceDate());
                record.setRecordedBy(SecurityUtils.current().id());
            }
            record.setStatus("LEAVE");
            record.setRemark("请假审批通过");
            if (record.getId() == null) records.insert(record); else records.updateById(record);
        }
    }
}
