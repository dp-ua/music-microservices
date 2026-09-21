package com.music.resource.controller.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.music.resource.advice.BaseExceptionHandler;
import com.music.resource.advice.ErrorResponseFactory;
import com.music.resource.dto.ErrorResponse;
import com.music.resource.exception.ResourceException;
import com.music.resource.exception.ResourceNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice(basePackages = "com.music.resource.controller")
public class ControllerAdvice extends BaseExceptionHandler {

    public ControllerAdvice(ErrorResponseFactory responseFactory) {
        super(responseFactory);
    }

    @ExceptionHandler(ResourceException.class)
    public ResponseEntity<ErrorResponse> handleResourceException(ResourceException e) {
        log.error("Resource error. code: {}, message: {}", e.getErrorCode(), e.buildFullMessage(), e);
        return super.handleServiceException(e);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException e) {
        log.warn("Resource not found. code: {}, message: {}", e.getErrorCode(), e.buildFullMessage());
        return super.handleServiceException(e);
    }

}