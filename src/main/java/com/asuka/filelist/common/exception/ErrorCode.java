package com.asuka.filelist.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "Bad request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED),
    PERMISSION_DENIED("PERMISSION_DENIED", "Permission denied", HttpStatus.FORBIDDEN),
    STORAGE_NOT_FOUND("STORAGE_NOT_FOUND", "Storage not found", HttpStatus.NOT_FOUND),
    OBJECT_NOT_FOUND("OBJECT_NOT_FOUND", "Object not found", HttpStatus.NOT_FOUND),
    DRIVER_NOT_SUPPORTED("DRIVER_NOT_SUPPORTED", "Driver operation is not supported", HttpStatus.METHOD_NOT_ALLOWED),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    AI_SERVICE_ERROR("AI_SERVICE_ERROR", "AI service error", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
