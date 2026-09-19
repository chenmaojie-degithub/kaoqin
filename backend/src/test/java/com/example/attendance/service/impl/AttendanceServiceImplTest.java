package com.example.attendance.service.impl;

import com.example.attendance.dto.AttendanceCheckInRequest;
import com.example.attendance.entity.*;
import com.example.attendance.mapper.*;
import com.example.attendance.security.LoginUser;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AttendanceServiceImplTest {
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test void passwordCheckInUsesJwtStudentAndServerTime() {
        AttendanceTaskMapper tasks = mock(AttendanceTaskMapper.class); AttendanceRecordMapper records = mock(AttendanceRecordMapper.class);
        CourseClassMapper courseClasses = mock(CourseClassMapper.class); CourseMapper courses = mock(CourseMapper.class);
        StudentMapper students = mock(StudentMapper.class); TeacherMapper teachers = mock(TeacherMapper.class);
        LeaveRequestMapper leaves = mock(LeaveRequestMapper.class); AttendanceSessionMapper sessions = mock(AttendanceSessionMapper.class);
        AttendanceServiceImpl service = new AttendanceServiceImpl(tasks, records, courseClasses, courses, students, teachers, leaves, sessions);

        User user = new User(); user.setId(20L); user.setUsername("student"); user.setPassword("test"); user.setRole("STUDENT"); user.setStatus(1);
        LoginUser loginUser = new LoginUser(user); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
        Student student = new Student(); student.setId(2L); student.setUserId(20L); student.setClassId(3L);
        AttendanceTask task = new AttendanceTask(); task.setId(4L); task.setAttendanceDate(LocalDate.now()); task.setStatus("OPEN");
        AttendanceSession session = new AttendanceSession(); session.setId(5L); session.setTaskId(4L); session.setCourseId(6L); session.setClassId(3L); session.setSignCode("583214"); session.setStatus("OPEN"); session.setExpiresAt(LocalDateTime.now().plusSeconds(30));
        when(students.selectOne(any())).thenReturn(student); when(sessions.selectById(5L)).thenReturn(session); when(tasks.selectById(4L)).thenReturn(task); when(records.selectOne(any())).thenReturn(null);

        service.checkIn(new AttendanceCheckInRequest(5L, "583214"));

        ArgumentCaptor<AttendanceRecord> saved = ArgumentCaptor.forClass(AttendanceRecord.class); verify(records).insert(saved.capture());
        assertAll(() -> assertEquals(2L, saved.getValue().getStudentId()), () -> assertEquals(5L, saved.getValue().getSessionId()),
                () -> assertEquals("NORMAL", saved.getValue().getStatus()), () -> assertNotNull(saved.getValue().getCheckInTime()),
                () -> assertEquals(20L, saved.getValue().getRecordedBy()));
    }
}
