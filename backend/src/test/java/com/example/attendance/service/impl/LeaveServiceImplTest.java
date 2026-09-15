package com.example.attendance.service.impl;

import com.example.attendance.dto.LeaveApprovalRequest;
import com.example.attendance.entity.AttendanceRecord;
import com.example.attendance.entity.AttendanceTask;
import com.example.attendance.entity.CourseClass;
import com.example.attendance.entity.LeaveRequest;
import com.example.attendance.entity.Student;
import com.example.attendance.entity.User;
import com.example.attendance.mapper.AttendanceRecordMapper;
import com.example.attendance.mapper.AttendanceTaskMapper;
import com.example.attendance.mapper.CourseClassMapper;
import com.example.attendance.mapper.LeaveRequestMapper;
import com.example.attendance.mapper.StudentMapper;
import com.example.attendance.mapper.TeacherMapper;
import com.example.attendance.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveServiceImplTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approvingLeaveUpdatesExistingRecordUsingDatabaseUniqueIdentity() {
        LeaveRequestMapper leaves = mock(LeaveRequestMapper.class);
        StudentMapper students = mock(StudentMapper.class);
        TeacherMapper teachers = mock(TeacherMapper.class);
        CourseClassMapper courseClasses = mock(CourseClassMapper.class);
        AttendanceTaskMapper tasks = mock(AttendanceTaskMapper.class);
        AttendanceRecordMapper records = mock(AttendanceRecordMapper.class);
        LeaveServiceImpl service = new LeaveServiceImpl(leaves, students, teachers, courseClasses, tasks, records);

        User admin = new User();
        admin.setId(1L); admin.setUsername("admin"); admin.setPassword("test"); admin.setRole("ADMIN"); admin.setStatus(1);
        LoginUser loginUser = new LoginUser(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));

        LeaveRequest leave = new LeaveRequest();
        leave.setId(7L); leave.setStudentId(2L); leave.setStatus("PENDING");
        leave.setStartTime(LocalDateTime.of(2026, 9, 14, 8, 0));
        leave.setEndTime(LocalDateTime.of(2026, 9, 14, 12, 0));
        Student student = new Student(); student.setId(2L); student.setClassId(3L);
        CourseClass assignment = new CourseClass(); assignment.setId(4L); assignment.setClassId(3L); assignment.setCourseId(5L);
        AttendanceTask task = new AttendanceTask(); task.setId(8L); task.setCourseClassId(4L); task.setAttendanceDate(LocalDate.of(2026, 9, 14));
        AttendanceRecord existing = new AttendanceRecord();
        existing.setId(9L); existing.setTaskId(6L); existing.setStudentId(2L); existing.setCourseId(5L);
        existing.setAttendanceDate(LocalDate.of(2026, 9, 14)); existing.setStatus("ABSENT");

        when(leaves.selectById(7L)).thenReturn(leave);
        when(students.selectById(2L)).thenReturn(student);
        when(courseClasses.selectList(any())).thenReturn(List.of(assignment));
        when(tasks.selectList(any())).thenReturn(List.of(task));
        when(records.selectOne(any())).thenReturn(existing);

        service.approve(7L, new LeaveApprovalRequest("APPROVED", "同意"));

        assertEquals("LEAVE", existing.getStatus());
        assertEquals("请假审批通过", existing.getRemark());
        verify(records).updateById(existing);
        verify(records, never()).insert(any(AttendanceRecord.class));
    }
}
