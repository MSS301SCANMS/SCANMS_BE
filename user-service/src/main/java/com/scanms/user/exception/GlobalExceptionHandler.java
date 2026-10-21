package com.scanms.user.exception;

import com.scanms.user.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiResponse.error(exception.getStatusCode().value(), exception.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String state = cause instanceof SQLException sql ? sql.getSQLState() : null;
        log.warn("Profile persistence rejected (SQL state {})", state);
        if ("22001".equals(state)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400,
                    "Thông tin hồ sơ vượt quá độ dài cho phép. Vui lòng rút gọn nội dung."));
        }
        String message = "23505".equals(state)
                ? "Identity conflicts with an existing profile; retry or contact support"
                : "Không thể lưu hồ sơ vì dữ liệu không hợp lệ. Vui lòng kiểm tra lại thông tin.";
        return ResponseEntity.status(409).body(ApiResponse.error(409, message));
    }

    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiResponse<Void>> handleAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.INVALID_REQUEST.getMessage())
                : exception.getMessage();
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(), message));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        // Log day du stack trace o day vi day la exception KHONG duoc xu ly rieng (@ExceptionHandler da
        // "nuot" no nen Spring se khong tu dong log nhu binh thuong) -> neu khong log thi se khong co
        // cach nao biet duoc nguyen nhan that su tren server, chi thay "An unexpected error occurred" o FE.
        log.error("Loi khong luong truoc duoc tai {}", exception.getClass().getSimpleName(), exception);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }
}

