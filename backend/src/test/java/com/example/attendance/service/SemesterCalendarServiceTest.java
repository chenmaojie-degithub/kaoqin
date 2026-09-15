package com.example.attendance.service;

import com.example.attendance.config.SemesterProperties;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.vo.SemesterWeekVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SemesterCalendarServiceTest {
    private SemesterCalendarService service;

    @BeforeEach
    void setUp() {
        SemesterProperties properties = new SemesterProperties();
        properties.setAcademicYear("2026-2027");
        properties.setName("第一学期");
        properties.setStartDate(LocalDate.of(2026, 9, 1));
        properties.setEndDate(LocalDate.of(2027, 1, 17));
        service = new SemesterCalendarService(properties);
    }

    @Test
    void calculatesWeekFromMondayContainingSemesterStart() {
        SemesterWeekVO result = service.getCalendar(LocalDate.of(2026, 9, 15), null);
        assertEquals(3, result.currentWeek());
        assertEquals(LocalDate.of(2026, 9, 14), result.weekStart());
        assertEquals(LocalDate.of(2026, 9, 20), result.weekEnd());
    }

    @Test
    void clampsDatesOutsideSemester() {
        assertEquals(1, service.getCalendar(LocalDate.of(2026, 8, 20), null).currentWeek());
        SemesterWeekVO ended = service.getCalendar(LocalDate.of(2027, 2, 1), null);
        assertEquals("ENDED", ended.status());
        assertEquals(ended.totalWeeks(), ended.currentWeek());
    }

    @Test
    void rejectsWeekOutsideSemester() {
        assertThrows(BusinessException.class,
                () -> service.getCalendar(LocalDate.of(2026, 9, 15), 99));
    }
}
