package com.example.attendance.service;

import com.example.attendance.dto.CourseScheduleRequest;
import com.example.attendance.dto.ScheduleEditMode;
import com.example.attendance.entity.CourseSchedule;

public interface CourseScheduleService {
    CourseSchedule create(Long courseClassId, CourseScheduleRequest request);
    CourseSchedule update(Long id, Long courseClassId, CourseScheduleRequest request, ScheduleEditMode mode, Integer currentWeek);
    void delete(Long id);
}
