package com.music.resource.controller.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.music.common.advice.BaseExceptionHandler;
import com.music.common.dto.ErrorResponse;
import com.music.resource.exception.ResourceException;
import com.music.resource.exception.ResourceNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice(basePackages = "com.music.resource.controller")
public class ControllerAdvice extends BaseExceptionHandler {

    @ExceptionHandler(ResourceException.class)
    public ResponseEntity<ErrorResponse> handleResourceException(ResourceException e) {
        log.error("Resource error. service: {}, code: {}, message: {}",
                e.getService(), e.getErrorCode(), e.buildFullMessage(), e);
        return super.handleServiceException(e);   // ← переиспользуем логику базового
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException e) {
        log.warn("Resource not found. service: {}, code: {}, message: {}",
                e.getService(), e.getErrorCode(), e.buildFullMessage());
        return super.handleServiceException(e);
    }
}