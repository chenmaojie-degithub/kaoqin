package com.example.attendance.controller;

import com.example.attendance.config.SemesterProperties;
import com.example.attendance.entity.*;
import com.example.attendance.mapper.*;
import com.example.attendance.security.LoginUser;
import com.example.attendance.service.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StudentPortalControllerTest {
    private final AttendanceService attendance = mock(AttendanceService.class);
    private final StudentMapper students = mock(StudentMapper.class);
    private final CourseScheduleMapper schedules = mock(CourseScheduleMapper.class);
    private final StudentPortalController controller = new StudentPortalController(students, mock(UserMapper.class), mock(ClassInfoMapper.class),
            mock(CourseClassMapper.class), schedules, mock(CourseMapper.class), mock(TeacherMapper.class), mock(AttendanceTaskMapper.class),
            mock(AttendanceRecordMapper.class), mock(LeaveRequestMapper.class), attendance, mock(PasswordEncoder.class), new SemesterProperties(), mock(SemesterCalendarService.class));
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @BeforeEach void setup() {
        User user = new User(); user.setId(1L); user.setRole("STUDENT"); user.setUsername("test");
        LoginUser login = new LoginUser(user); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null,login.getAuthorities()));
        Student student = new Student(); student.setId(2L); student.setClassId(3L); when(students.selectOne(any())).thenReturn(student);
    }
    @Test void tasksRemainDiscoverableWithoutAnyScheduleAndPreserveAttendanceStatus() {
        Map<String,Object> row = new LinkedHashMap<>(Map.of("taskId",4L,"status","NORMAL","courseName","Test course"));
        when(attendance.records(null,2L,null,LocalDate.now())).thenReturn(List.of(row));
        when(attendance.studentSession(4L)).thenReturn(Map.of("status","EXPIRED","checkInState","EXPIRED","checkedIn",false));
        var result = controller.todayCheckIns().data();
        assertEquals(1,result.size()); assertEquals("NORMAL",result.get(0).get("status")); assertEquals("EXPIRED",result.get(0).get("sessionStatus"));
        assertFalse(result.get(0).containsKey("signCode")); verifyNoInteractions(schedules);
    }
    @Test void oneSignedTaskDoesNotHideAnotherPendingTaskOnDashboard() {
        Map<String,Object> signed = new LinkedHashMap<>(Map.of("taskId",4L,"status","NORMAL","attendanceDate",LocalDate.now()));
        Map<String,Object> pending = new LinkedHashMap<>(Map.of("taskId",5L,"status","NORMAL","attendanceDate",LocalDate.now()));
        when(attendance.records(null,2L,null,null)).thenReturn(List.of(signed,pending));
        when(attendance.records(null,2L,null,LocalDate.now())).thenReturn(List.of(signed,pending));
        when(attendance.studentSession(4L)).thenReturn(Map.of("status","EXPIRED","checkInState","SIGNED","checkedIn",true));
        when(attendance.studentSession(5L)).thenReturn(Map.of("status","OPEN","checkInState","OPEN","checkedIn",false));
        assertEquals("OPEN",controller.dashboard().data().get("todayCheckInState"));
    }
}
