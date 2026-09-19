package com.example.attendance.dto;
import jakarta.validation.constraints.NotNull;
public record AttendanceSessionCreateRequest(@NotNull Long taskId) {}
