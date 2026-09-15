package com.example.attendance.dto;
import jakarta.validation.constraints.*; import java.time.LocalDate;
public record AttendanceTaskCreateRequest(@NotNull Long courseClassId,@NotNull LocalDate attendanceDate,String remark){}

