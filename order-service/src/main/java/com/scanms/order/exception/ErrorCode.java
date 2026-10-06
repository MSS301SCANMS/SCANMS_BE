package com.scanms.order.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(400, HttpStatus.BAD_REQUEST, "Invalid request"),
    UNAUTHORIZED(401, HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(403, HttpStatus.FORBIDDEN, "Access is denied"),
    RESOURCE_NOT_FOUND(404, HttpStatus.NOT_FOUND, "Resource not found"),
    CONFLICT(409, HttpStatus.CONFLICT, "Resource conflict"),
    INTERNAL_SERVER_ERROR(500, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
    ORDER_NOT_FOUND(4041, HttpStatus.NOT_FOUND, "Order not found"),
    SELLER_ORDER_NOT_FOUND(4042, HttpStatus.NOT_FOUND, "Seller order not found"),
    ORDER_ITEM_NOT_FOUND(4043, HttpStatus.NOT_FOUND, "Order item not found"),
    SHIPMENT_NOT_FOUND(4044, HttpStatus.NOT_FOUND, "Shipment not found"),
    RETURN_REQUEST_NOT_FOUND(4045, HttpStatus.NOT_FOUND, "Return request not found"),
    INVALID_ORDER_STATUS(4001, HttpStatus.BAD_REQUEST, "Invalid order status for this operation"),
    RETURN_WINDOW_EXPIRED(4002, HttpStatus.BAD_REQUEST, "Return deadline has expired (14-day limit)"),
    INVALID_RETURN_QUANTITY(4003, HttpStatus.BAD_REQUEST, "Requested return quantity exceeds eligible purchased quantity"),
    ORDER_NOT_DELIVERED(4004, HttpStatus.BAD_REQUEST, "Cannot request return on an order that has not been delivered");

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

