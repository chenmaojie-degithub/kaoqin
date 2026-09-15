package com.example.attendance.service.impl;

import com.example.attendance.dto.CourseScheduleRequest;
import com.example.attendance.dto.ScheduleEditMode;
import com.example.attendance.config.SemesterProperties;
import com.example.attendance.entity.CourseClass;
import com.example.attendance.entity.CourseSchedule;
import com.example.attendance.entity.User;
import com.example.attendance.mapper.ClassInfoMapper;
import com.example.attendance.mapper.CourseClassMapper;
import com.example.attendance.mapper.CourseMapper;
import com.example.attendance.mapper.CourseScheduleMapper;
import com.example.attendance.mapper.TeacherMapper;
import com.example.attendance.security.LoginUser;
import com.example.attendance.service.SemesterCalendarService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseScheduleServiceImplTest {
    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void editingOnlyCurrentWeekSplitsOriginalRangeTransactionally() {
        CourseClassMapper courseClasses = mock(CourseClassMapper.class);
        CourseScheduleMapper schedules = mock(CourseScheduleMapper.class);
        ClassInfoMapper classes = mock(ClassInfoMapper.class);
        CourseMapper courses = mock(CourseMapper.class);
        TeacherMapper teachers = mock(TeacherMapper.class);
        SemesterProperties semesterProperties = new SemesterProperties();
        semesterProperties.setAcademicYear("2026-2027"); semesterProperties.setName("第一学期");
        semesterProperties.setStartDate(LocalDate.of(2026, 9, 1)); semesterProperties.setEndDate(LocalDate.of(2027, 1, 17));
        SemesterCalendarService semester = new SemesterCalendarService(semesterProperties);
        CourseScheduleServiceImpl service = new CourseScheduleServiceImpl(courseClasses, schedules, classes, courses, teachers, semester);
        loginAsAdmin();

        CourseClass assignment = new CourseClass();
        assignment.setId(3L); assignment.setClassId(4L); assignment.setTeacherId(5L);
        assignment.setAcademicYear("2026-2027"); assignment.setSemester("第一学期");
        CourseSchedule original = new CourseSchedule();
        original.setId(9L); original.setCourseClassId(3L); original.setWeekday(2);
        original.setStartTime(LocalTime.of(10, 10)); original.setEndTime(LocalTime.NOON);
        original.setLocation("A302"); original.setStartWeek(2); original.setEndWeek(8);
        when(schedules.selectById(9L)).thenReturn(original);
        when(courseClasses.selectById(3L)).thenReturn(assignment);
        when(classes.lockActiveById(4L)).thenReturn(4L);
        when(courseClasses.selectList(any())).thenReturn(List.of(assignment));
        when(schedules.selectOne(any())).thenReturn(null);

        CourseScheduleRequest request = new CourseScheduleRequest(2, LocalTime.of(10, 10), LocalTime.NOON, "B401", 2, 8);
        CourseSchedule changed = service.update(9L, 3L, request, ScheduleEditMode.CURRENT_WEEK, 5);

        assertEquals(4, original.getEndWeek());
        assertEquals(5, changed.getStartWeek());
        assertEquals(5, changed.getEndWeek());
        assertEquals("B401", changed.getLocation());
        ArgumentCaptor<CourseSchedule> inserted = ArgumentCaptor.forClass(CourseSchedule.class);
        verify(schedules, times(2)).insert(inserted.capture());
        assertEquals(6, inserted.getAllValues().get(0).getStartWeek());
        assertEquals(8, inserted.getAllValues().get(0).getEndWeek());
    }

    private void loginAsAdmin() {
        User user = new User(); user.setId(1L); user.setUsername("admin"); user.setPassword("test"); user.setRole("ADMIN"); user.setStatus(1);
        LoginUser loginUser = new LoginUser(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

}
