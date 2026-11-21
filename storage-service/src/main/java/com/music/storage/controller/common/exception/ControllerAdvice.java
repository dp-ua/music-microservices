package com.music.storage.controller.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.music.storage.advice.BaseExceptionHandler;
import com.music.storage.advice.ErrorResponseFactory;
import com.music.storage.dto.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice(basePackages = "com.music.storage.controller")
public class ControllerAdvice extends BaseExceptionHandler {

    private final ErrorResponseFactory responseFactory;

    public ControllerAdvice(ErrorResponseFactory responseFactory) {
        super(responseFactory);
        this.responseFactory = responseFactory;
    }

    /** Broken JSON or a storageType that is not STAGING / PERMANENT. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("Request body can't be read: {}", e.getMessage());
        return responseFactory.of("400",
                "Request body is invalid. storageType must be STAGING or PERMANENT, bucket and path are required");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return responseFactory.of("500", "Internal server error");
    }

}
