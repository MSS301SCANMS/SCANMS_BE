package com.scanms.payment.dto;

public record ApiResponse<T>(int code, String message, T result) {

    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>(200, "Success", result);
    }

    public static <T> ApiResponse<T> created(T result) {
        return new ApiResponse<>(201, "Created successfully", result);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}

