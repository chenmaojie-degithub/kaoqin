package com.example.attendance.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(int code, String message) { super(message); this.code = code; }
    public static BusinessException notFound(String message) { return new BusinessException(40400, message); }
    public static BusinessException forbidden(String message) { return new BusinessException(40300, message); }
    public static BusinessException conflict(String message) { return new BusinessException(40900, message); }
}

