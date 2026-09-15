package com.example.attendance.dto;
import jakarta.validation.constraints.*;
public record TeacherSaveRequest(@NotBlank String teacherNo,@NotBlank String name,String gender,String title,String phone,@Email String email,@NotBlank String username,String password,Integer status){}

