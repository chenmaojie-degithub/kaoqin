package com.example.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record CourseScheduleRequest(
        @NotNull @Min(value = 1, message = "星期必须在1到7之间") @Max(value = 7, message = "星期必须在1到7之间") Integer weekday,
        @NotNull(message = "请选择开始节次") LocalTime startTime,
        @NotNull(message = "请选择结束节次") LocalTime endTime,
        @NotBlank(message = "请填写上课地点") @Size(max = 100, message = "上课地点不能超过100个字符") String location,
        @NotNull(message = "请选择开始周") @Min(value = 1, message = "开始周不能小于1") Integer startWeek,
        @NotNull(message = "请选择结束周") @Min(value = 1, message = "结束周不能小于1") Integer endWeek
) {}
