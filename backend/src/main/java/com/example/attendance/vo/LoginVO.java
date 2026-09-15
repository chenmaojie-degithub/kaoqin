package com.example.attendance.vo;
public record LoginVO(String token, String tokenType, long expiresIn, UserVO user) {}

