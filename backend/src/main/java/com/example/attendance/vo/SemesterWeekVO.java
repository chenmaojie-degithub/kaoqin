package com.example.attendance.vo;

import java.time.LocalDate;
import java.util.List;

public record SemesterWeekVO(
        String academicYear,
        String semester,
        LocalDate startDate,
        LocalDate endDate,
        int totalWeeks,
        int currentWeek,
        String status,
        LocalDate today,
        int displayWeek,
        LocalDate weekStart,
        LocalDate weekEnd,
        List<LocalDate> weekDates
) {}
