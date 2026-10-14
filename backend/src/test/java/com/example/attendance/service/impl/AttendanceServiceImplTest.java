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
        when(students.selectOne(any())).thenReturn(student); when(sessions.selectById(5L)).thenReturn(session); when(tasks.selectById(4L)).thenReturn(task); when(tasks.lockById(4L)).thenReturn(task); when(records.selectOne(any())).thenReturn(null);

        service.checkIn(new AttendanceCheckInRequest(5L, "583214"));

        ArgumentCaptor<AttendanceRecord> saved = ArgumentCaptor.forClass(AttendanceRecord.class); verify(records).insert(saved.capture());
        assertAll(() -> assertEquals(2L, saved.getValue().getStudentId()), () -> assertEquals(5L, saved.getValue().getSessionId()),
                () -> assertEquals("NORMAL", saved.getValue().getStatus()), () -> assertNotNull(saved.getValue().getCheckInTime()),
                () -> assertEquals(20L, saved.getValue().getRecordedBy()));
    }

    private final AttendanceTaskMapper tasks = mock(AttendanceTaskMapper.class);
    private final AttendanceRecordMapper records = mock(AttendanceRecordMapper.class);
    private final CourseClassMapper assignments = mock(CourseClassMapper.class);
    private final StudentMapper students = mock(StudentMapper.class);
    private final TeacherMapper teachers = mock(TeacherMapper.class);
    private final AttendanceSessionMapper sessions = mock(AttendanceSessionMapper.class);
    private final AttendanceServiceImpl service = new AttendanceServiceImpl(tasks, records, assignments, mock(CourseMapper.class), students, teachers, mock(LeaveRequestMapper.class), sessions);
    private AttendanceSession session;
    private AttendanceTask task;

    @BeforeEach void prepare() {
        login("STUDENT");
        Student student = new Student(); student.setId(2L); student.setClassId(3L);
        task = new AttendanceTask(); task.setId(4L); task.setCourseClassId(7L); task.setAttendanceDate(LocalDate.now()); task.setStatus("OPEN");
        CourseClass assignment = new CourseClass(); assignment.setId(7L); assignment.setClassId(3L); assignment.setTeacherId(9L);
        session = new AttendanceSession(); session.setId(5L); session.setTaskId(4L); session.setCourseId(6L); session.setClassId(3L); session.setStatus("OPEN"); session.setSignCode("583214"); session.setExpiresAt(LocalDateTime.now().plusSeconds(45));
        when(students.selectOne(any())).thenReturn(student); when(tasks.selectById(4L)).thenReturn(task); when(tasks.lockById(4L)).thenReturn(task);
        when(assignments.selectById(7L)).thenReturn(assignment); when(sessions.selectById(5L)).thenReturn(session); when(sessions.selectOne(any())).thenReturn(session);
    }

    @Test void studentStatusPreservesExpiredAndClosedSessionsWithoutLeakingCodeOrWriting() {
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        var expired = service.studentSession(4L);
        assertEquals("EXPIRED", expired.get("checkInState")); assertFalse(expired.containsKey("signCode"));
        assertEquals(false, expired.get("checkedIn")); verify(sessions, never()).updateById(any(AttendanceSession.class));
        session.setStatus("CLOSED"); assertEquals("CLOSED", service.studentSession(4L).get("checkInState"));
        when(sessions.selectOne(any())).thenReturn(null); assertEquals("WAITING", service.studentSession(4L).get("checkInState"));
    }

    @Test void signedReceiptSurvivesExpiredSessionAndReopening() {
        AttendanceRecord receipt = new AttendanceRecord(); receipt.setSessionId(1L); receipt.setCheckInTime(LocalDateTime.now().minusMinutes(2));
        when(records.selectOne(any())).thenReturn(receipt);
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertEquals("SIGNED", service.studentSession(4L).get("checkInState"));
        session.setExpiresAt(LocalDateTime.now().plusSeconds(30)); assertEquals("SIGNED", service.studentSession(4L).get("checkInState"));
        assertThrows(com.example.attendance.exception.BusinessException.class, () -> service.checkIn(new AttendanceCheckInRequest(5L, "583214")));
        verify(records, never()).insert(any(AttendanceRecord.class)); verify(records, never()).updateById(any(AttendanceRecord.class));
    }

    @Test void checkInRejectsExpiredClosedWrongCodeAndOtherClass() {
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1)); assertRejected("签到码已过期，请联系教师重新开启签到");
        session.setExpiresAt(LocalDateTime.now().plusSeconds(45)); session.setStatus("CLOSED"); assertRejected("签到已结束");
        session.setStatus("OPEN"); task.setStatus("CLOSED"); assertRejected("签到已结束");
        task.setStatus("OPEN"); session.setSignCode("111111"); assertRejected("签到密码错误");
        session.setClassId(99L); assertRejected("你不属于该课程班级");
        verify(records, never()).insert(any(AttendanceRecord.class));
    }

    @Test void teacherCanCreateOnlyOwnAssignment() {
        login("TEACHER"); Teacher teacher = new Teacher(); teacher.setId(9L); when(teachers.selectOne(any())).thenReturn(teacher);
        service.createTask(new com.example.attendance.dto.AttendanceTaskCreateRequest(7L, LocalDate.now(), "test"));
        verify(tasks).insert(any(AttendanceTask.class));
        teacher.setId(10L);
        var error = assertThrows(com.example.attendance.exception.BusinessException.class, () -> service.createTask(new com.example.attendance.dto.AttendanceTaskCreateRequest(7L, LocalDate.now(), "test")));
        assertEquals(40300, error.getCode());
    }

    @Test void existingExceptionBecomesReceiptOnlyOnceAndTaskIsLockedBeforeReadingReceipt() {
        AttendanceRecord existing = new AttendanceRecord(); existing.setId(8L); existing.setStatus("ABSENT");
        when(records.selectOne(any())).thenReturn(existing);
        service.checkIn(new AttendanceCheckInRequest(5L,"583214"));
        assertEquals("NORMAL",existing.getStatus()); assertEquals(5L,existing.getSessionId());
        var ordered = inOrder(tasks,records); ordered.verify(tasks).lockById(4L); ordered.verify(records).selectOne(any()); ordered.verify(records).updateById(existing);
        assertRejected("你已经完成本节课签到"); verify(records,times(1)).updateById(existing);
    }

    private void assertRejected(String message) {
        assertEquals(message, assertThrows(com.example.attendance.exception.BusinessException.class, () -> service.checkIn(new AttendanceCheckInRequest(5L, "583214"))).getMessage());
    }
    private void login(String role) {
        User user = new User(); user.setId(20L); user.setRole(role); user.setUsername("test"); user.setStatus(1);
        LoginUser login = new LoginUser(user); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null, login.getAuthorities()));
    }
}
