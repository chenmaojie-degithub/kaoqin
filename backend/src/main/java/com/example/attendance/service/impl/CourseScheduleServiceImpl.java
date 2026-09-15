package com.example.attendance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.attendance.dto.CourseScheduleRequest;
import com.example.attendance.dto.ScheduleEditMode;
import com.example.attendance.entity.Course;
import com.example.attendance.entity.CourseClass;
import com.example.attendance.entity.CourseSchedule;
import com.example.attendance.entity.Teacher;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.mapper.ClassInfoMapper;
import com.example.attendance.mapper.CourseClassMapper;
import com.example.attendance.mapper.CourseMapper;
import com.example.attendance.mapper.CourseScheduleMapper;
import com.example.attendance.mapper.TeacherMapper;
import com.example.attendance.security.SecurityUtils;
import com.example.attendance.service.CourseScheduleService;
import com.example.attendance.service.SemesterCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseScheduleServiceImpl implements CourseScheduleService {
    private static final Map<LocalTime, Integer> START_PERIODS = Map.of(
            LocalTime.of(8, 0), 1, LocalTime.of(10, 10), 3, LocalTime.of(13, 30), 5,
            LocalTime.of(15, 40), 7, LocalTime.of(18, 30), 9);
    private static final Map<LocalTime, Integer> END_PERIODS = Map.of(
            LocalTime.of(9, 50), 2, LocalTime.of(12, 0), 4, LocalTime.of(15, 20), 6,
            LocalTime.of(17, 30), 8, LocalTime.of(20, 20), 10);

    private final CourseClassMapper courseClasses;
    private final CourseScheduleMapper schedules;
    private final ClassInfoMapper classes;
    private final CourseMapper courses;
    private final TeacherMapper teachers;
    private final SemesterCalendarService semesterCalendarService;

    @Override
    @Transactional
    public CourseSchedule create(Long courseClassId, CourseScheduleRequest request) {
        CourseClass assignment = requiredAssignment(courseClassId);
        assertCanManage(assignment);
        validate(request);
        lockClasses(assignment.getClassId());
        assertNoConflict(assignment, request, request.startWeek(), request.endWeek(), null);
        CourseSchedule schedule = fromRequest(courseClassId, request, request.startWeek(), request.endWeek());
        schedules.insert(schedule);
        return schedule;
    }

    @Override
    @Transactional
    public CourseSchedule update(Long id, Long targetCourseClassId, CourseScheduleRequest request,
                                 ScheduleEditMode mode, Integer currentWeek) {
        CourseSchedule original = requiredSchedule(id);
        CourseClass sourceAssignment = requiredAssignment(original.getCourseClassId());
        CourseClass targetAssignment = requiredAssignment(targetCourseClassId);
        assertCanManage(sourceAssignment);
        assertCanManage(targetAssignment);
        validate(request);
        lockClasses(sourceAssignment.getClassId(), targetAssignment.getClassId());

        if (mode == ScheduleEditMode.ALL) {
            assertNoConflict(targetAssignment, request, request.startWeek(), request.endWeek(), id);
            fill(original, targetCourseClassId, request, request.startWeek(), request.endWeek());
            schedules.updateById(original);
            return original;
        }

        int week = requiredCurrentWeek(currentWeek, original);
        int newEndWeek = mode == ScheduleEditMode.CURRENT_WEEK ? week : original.getEndWeek();
        assertNoConflict(targetAssignment, request, week, newEndWeek, id);

        if (mode == ScheduleEditMode.FROM_CURRENT_WEEK) {
            if (week == original.getStartWeek()) {
                fill(original, targetCourseClassId, request, week, original.getEndWeek());
                schedules.updateById(original);
                return original;
            }
            original.setEndWeek(week - 1);
            schedules.updateById(original);
            CourseSchedule changed = fromRequest(targetCourseClassId, request, week, newEndWeek);
            schedules.insert(changed);
            return changed;
        }

        int originalStart = original.getStartWeek();
        int originalEnd = original.getEndWeek();
        if (originalStart == originalEnd) {
            fill(original, targetCourseClassId, request, week, week);
            schedules.updateById(original);
            return original;
        }
        if (originalStart < week) {
            original.setEndWeek(week - 1);
        } else {
            original.setStartWeek(week + 1);
        }
        schedules.updateById(original);
        if (originalStart < week && week < originalEnd) {
            CourseSchedule right = copyOf(original);
            right.setId(null);
            right.setStartWeek(week + 1);
            right.setEndWeek(originalEnd);
            schedules.insert(right);
        }
        CourseSchedule changed = fromRequest(targetCourseClassId, request, week, week);
        schedules.insert(changed);
        return changed;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        CourseSchedule schedule = requiredSchedule(id);
        CourseClass assignment = requiredAssignment(schedule.getCourseClassId());
        assertCanManage(assignment);
        schedules.deleteById(id);
    }

    private void validate(CourseScheduleRequest request) {
        validatePeriodBoundary(request.startTime(), request.endTime());
        int totalWeeks = semesterCalendarService.getCalendar(null).totalWeeks();
        if (request.endWeek() < request.startWeek()) {
            throw BusinessException.conflict("结束周不能早于开始周");
        }
        if (request.startWeek() > totalWeeks || request.endWeek() > totalWeeks) {
            throw BusinessException.conflict("课程生效周次必须在第1周至第" + totalWeeks + "周之间");
        }
    }

    private int requiredCurrentWeek(Integer currentWeek, CourseSchedule original) {
        if (currentWeek == null) throw BusinessException.conflict("按当前周修改时必须指定当前查看周");
        if (currentWeek < original.getStartWeek() || currentWeek > original.getEndWeek()) {
            throw BusinessException.conflict("当前查看周不在该课程原生效范围内");
        }
        return currentWeek;
    }

    private void assertNoConflict(CourseClass assignment, CourseScheduleRequest request,
                                  int startWeek, int endWeek, Long excludedId) {
        List<Long> assignmentIds = courseClasses.selectList(Wrappers.<CourseClass>lambdaQuery()
                        .eq(CourseClass::getClassId, assignment.getClassId())
                        .eq(CourseClass::getAcademicYear, assignment.getAcademicYear())
                        .eq(CourseClass::getSemester, assignment.getSemester()))
                .stream().map(CourseClass::getId).toList();
        var query = Wrappers.<CourseSchedule>lambdaQuery()
                .in(CourseSchedule::getCourseClassId, assignmentIds)
                .eq(CourseSchedule::getWeekday, request.weekday())
                .lt(CourseSchedule::getStartTime, request.endTime())
                .gt(CourseSchedule::getEndTime, request.startTime())
                .le(CourseSchedule::getStartWeek, endWeek)
                .ge(CourseSchedule::getEndWeek, startWeek);
        if (excludedId != null) query.ne(CourseSchedule::getId, excludedId);
        CourseSchedule conflict = schedules.selectOne(query.last("LIMIT 1"));
        if (conflict == null) return;
        CourseClass conflictAssignment = courseClasses.selectById(conflict.getCourseClassId());
        Course conflictCourse = conflictAssignment == null ? null : courses.selectById(conflictAssignment.getCourseId());
        String courseName = conflictCourse == null ? "其他课程" : conflictCourse.getName();
        throw BusinessException.conflict(String.format(
                "时间冲突：该班级第%d-%d周周%s %s-%s 已安排%s（第%d-%d周）",
                startWeek, endWeek, weekdayText(request.weekday()), request.startTime(), request.endTime(),
                courseName, conflict.getStartWeek(), conflict.getEndWeek()));
    }

    private void lockClasses(Long... classIds) {
        for (Long classId : java.util.Arrays.stream(classIds).filter(Objects::nonNull).distinct().sorted().toList()) {
            if (classes.lockActiveById(classId) == null) throw BusinessException.notFound("班级不存在");
        }
    }

    private CourseClass requiredAssignment(Long id) {
        CourseClass assignment = courseClasses.selectById(id);
        if (assignment == null) throw BusinessException.notFound("课程班级关联不存在");
        return assignment;
    }

    private CourseSchedule requiredSchedule(Long id) {
        CourseSchedule schedule = schedules.selectById(id);
        if (schedule == null) throw BusinessException.notFound("课程时间安排不存在");
        return schedule;
    }

    private CourseSchedule fromRequest(Long courseClassId, CourseScheduleRequest request, int startWeek, int endWeek) {
        CourseSchedule schedule = new CourseSchedule();
        fill(schedule, courseClassId, request, startWeek, endWeek);
        return schedule;
    }

    private void fill(CourseSchedule schedule, Long courseClassId, CourseScheduleRequest request, int startWeek, int endWeek) {
        schedule.setCourseClassId(courseClassId);
        schedule.setWeekday(request.weekday());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setLocation(request.location().trim());
        schedule.setStartWeek(startWeek);
        schedule.setEndWeek(endWeek);
    }

    private CourseSchedule copyOf(CourseSchedule source) {
        CourseSchedule copy = new CourseSchedule();
        copy.setCourseClassId(source.getCourseClassId());
        copy.setWeekday(source.getWeekday());
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setLocation(source.getLocation());
        copy.setStartWeek(source.getStartWeek());
        copy.setEndWeek(source.getEndWeek());
        return copy;
    }

    private void assertCanManage(CourseClass assignment) {
        if ("ADMIN".equals(SecurityUtils.current().role())) return;
        if (!"TEACHER".equals(SecurityUtils.current().role())) throw BusinessException.forbidden("无权修改课程安排");
        Teacher teacher = teachers.selectOne(Wrappers.<Teacher>lambdaQuery().eq(Teacher::getUserId, SecurityUtils.current().id()));
        if (teacher == null || !teacher.getId().equals(assignment.getTeacherId())) {
            throw BusinessException.forbidden("只能修改本人负责的课程安排");
        }
    }

    private void validatePeriodBoundary(LocalTime startTime, LocalTime endTime) {
        Integer startPeriod = START_PERIODS.get(startTime);
        Integer endPeriod = END_PERIODS.get(endTime);
        if (startPeriod == null || endPeriod == null || endPeriod < startPeriod + 1) {
            throw BusinessException.conflict("课程时间必须按两节课时块安排：1-2、3-4、5-6、7-8或9-10节，可连续选择多个课时块");
        }
    }

    private String weekdayText(int weekday) {
        return switch (weekday) {
            case 1 -> "一"; case 2 -> "二"; case 3 -> "三"; case 4 -> "四";
            case 5 -> "五"; case 6 -> "六"; default -> "日";
        };
    }
}
