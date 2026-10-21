package com.scanms.promotion.exception;

import com.scanms.promotion.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleMissingEndpoint(org.springframework.web.servlet.resource.NoResourceFoundException exception) {
        return ResponseEntity.status(404).body(ApiResponse.error(404, "API chưa tồn tại trên dịch vụ đang chạy. Vui lòng kiểm tra phiên bản backend."));
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> handleConcurrentUpdate(Exception exception) {
        return ResponseEntity.status(409).body(ApiResponse.error(409, "Dữ liệu đã thay đổi. Vui lòng tải lại trước khi sửa."));
    }

    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiResponse<Void>> handleAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), exception.getMessage()));
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

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiResponse.error(status, exception.getReason() == null ? "Request failed" : exception.getReason()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(403).body(ApiResponse.error(403, "Access denied"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }
}

