package com.example.attendance.dto;
import jakarta.validation.constraints.*; import java.time.LocalDate;
public record StudentSaveRequest(@NotBlank String studentNo,@NotBlank String name,String gender,String phone,@Email String email,@NotNull Long classId,LocalDate enrollmentDate,@NotBlank String username,String password,Integer status){}

