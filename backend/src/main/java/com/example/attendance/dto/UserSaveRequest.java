package com.example.attendance.dto;
import jakarta.validation.constraints.*;
public record UserSaveRequest(@NotBlank String username,String password,@NotBlank String realName,@Pattern(regexp="ADMIN|TEACHER|STUDENT") String role,Integer status){}

