package com.example.attendance.dto;
import jakarta.validation.constraints.*;
public record AttendanceRecordItem(@NotNull Long studentId,@NotBlank @Pattern(regexp="NORMAL|ABSENT",message="录入状态只能为正常或缺勤") String status,@Size(max=500) String remark){}
