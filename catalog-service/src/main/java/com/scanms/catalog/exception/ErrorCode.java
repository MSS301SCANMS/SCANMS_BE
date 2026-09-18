package com.scanms.catalog.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(400, HttpStatus.BAD_REQUEST, "Invalid request"),
    UNAUTHORIZED(401, HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(403, HttpStatus.FORBIDDEN, "Access is denied"),
    RESOURCE_NOT_FOUND(404, HttpStatus.NOT_FOUND, "Resource not found"),
    CONFLICT(409, HttpStatus.CONFLICT, "Resource conflict"),
    INTERNAL_SERVER_ERROR(500, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}

