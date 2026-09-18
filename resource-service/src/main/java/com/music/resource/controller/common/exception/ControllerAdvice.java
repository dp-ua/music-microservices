package com.music.resource.controller.common.exception;

import static com.music.resource.exception.ExceptionConstants.VALIDATION_EXCEPTION;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.music.resource.exception.ResourceException;
import com.music.resource.exception.ResourceNotFoundException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice(basePackages = "com.music.resource.controller")
public class ControllerAdvice {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Validation exception: {}", ex.getMessage());

        var violations = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        var errorResponse = ErrorResponse.builder()
                .message(VALIDATION_EXCEPTION + violations)
                .status(BAD_REQUEST.value())
                .timestamp(System.currentTimeMillis())
                .build();

        return ResponseEntity.status(BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ResourceException.class)
    public ResponseEntity<ErrorResponse> handleResourceException(ResourceException e) {
        log.error("Error resource uploading: {}", e.getMessage(), e);

        var errorResponse = ErrorResponse.builder()
                .message(e.getMessage())
                .status(BAD_REQUEST.value())
                .timestamp(System.currentTimeMillis())
                .build();

        return ResponseEntity.status(BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());

        var errorResponse = ErrorResponse.builder()
                .message(e.getMessage())
                .status(NOT_FOUND.value())
                .timestamp(System.currentTimeMillis())
                .build();

        return ResponseEntity.status(NOT_FOUND).body(errorResponse);
    }

}