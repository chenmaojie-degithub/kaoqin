package com.example.attendance.dto;
import jakarta.validation.constraints.*;
public record AttendanceCheckInRequest(@NotNull Long sessionId,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "签到密码必须是6位数字") String signCode) {}
