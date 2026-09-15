package com.example.attendance.service;

import com.example.attendance.config.SemesterProperties;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.vo.SemesterWeekVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SemesterCalendarService {
    private final SemesterProperties properties;

    public SemesterWeekVO getCalendar(Integer requestedWeek) {
        return getCalendar(LocalDate.now(), requestedWeek);
    }

    public SemesterWeekVO getCalendar(LocalDate today, Integer requestedWeek) {
        LocalDate startDate = properties.getStartDate();
        LocalDate endDate = properties.getEndDate();
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw BusinessException.conflict("学期开始日期和结束日期配置无效");
        }

        LocalDate firstMonday = startDate.minusDays(startDate.getDayOfWeek().getValue() - 1L);
        LocalDate lastMonday = endDate.minusDays(endDate.getDayOfWeek().getValue() - 1L);
        int totalWeeks = Math.toIntExact(ChronoUnit.WEEKS.between(firstMonday, lastMonday)) + 1;

        String status;
        int currentWeek;
        if (today.isBefore(startDate)) {
            status = "NOT_STARTED";
            currentWeek = 1;
        } else if (today.isAfter(endDate)) {
            status = "ENDED";
            currentWeek = totalWeeks;
        } else {
            status = "ACTIVE";
            currentWeek = Math.toIntExact(ChronoUnit.WEEKS.between(firstMonday,
                    today.minusDays(today.getDayOfWeek().getValue() - 1L))) + 1;
        }

        int displayWeek = requestedWeek == null ? currentWeek : requestedWeek;
        if (displayWeek < 1 || displayWeek > totalWeeks) {
            throw BusinessException.conflict("周次必须在第1周至第" + totalWeeks + "周之间");
        }
        LocalDate weekStart = firstMonday.plusWeeks(displayWeek - 1L);
        return new SemesterWeekVO(
                properties.getAcademicYear(), properties.getName(), startDate, endDate,
                totalWeeks, currentWeek, status, today, displayWeek, weekStart,
                weekStart.plusDays(6), IntStream.range(0, 7).mapToObj(weekStart::plusDays).toList());
    }
}
