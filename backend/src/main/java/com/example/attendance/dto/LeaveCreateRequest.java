package com.example.attendance.dto;
import jakarta.validation.constraints.*; import java.time.LocalDateTime;
public record LeaveCreateRequest(Long courseId,@NotNull LocalDateTime startTime,@NotNull LocalDateTime endTime,@NotBlank @Size(max=1000) String reason){}

