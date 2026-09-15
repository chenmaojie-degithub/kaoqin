package com.example.attendance.common;

import java.time.LocalDateTime;

public record ApiResponse<T>(int code, String message, T data, LocalDateTime timestamp) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(0, "操作成功", data, LocalDateTime.now()); }
    public static ApiResponse<Void> ok() { return ok(null); }
    public static ApiResponse<Void> error(int code, String message) { return new ApiResponse<>(code, message, null, LocalDateTime.now()); }
}

