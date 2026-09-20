package com.music.common.advice;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.music.common.dto.ErrorResponse;
import com.music.common.exception.BaseServiceException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class BaseExceptionHandler {

    private static final String BAD_REQUEST = "400";
    private static final String INVALID_ID = "Invalid value '%s' for ID. Must be a positive integer";
    private static final String INVALID_PARAM = "Invalid value '%s' for parameter '%s'";
    private static final String VALIDATION_ERROR = "Validation error";
    private static final String INVALID_FILE_FORMAT = "Invalid file format: %s. Only MP3 files are allowed";

    private final ErrorResponseFactory responseFactory;

    @ExceptionHandler(BaseServiceException.class)
    public ResponseEntity<ErrorResponse> handleServiceException(BaseServiceException ex) {
        log.warn("Service exception: {}", ex.buildFullMessage());
        return responseFactory.of(
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getService()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch: value='{}', requiredType={}", ex.getValue(), ex.getRequiredType());
        return isIntegerType(ex.getRequiredType())
                ? responseFactory.of(BAD_REQUEST, INVALID_ID.formatted(ex.getValue()))
                : responseFactory.of(BAD_REQUEST, INVALID_PARAM.formatted(ex.getValue(), ex.getName()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        var violation = ex.getConstraintViolations().iterator().next();
        var paramName = extractParamName(violation);

        var message = "id".equals(paramName)
                ? INVALID_ID.formatted(violation.getInvalidValue())
                : INVALID_PARAM.formatted(violation.getInvalidValue(), paramName);

        log.warn("Constraint violation: {}", message);
        return responseFactory.of(BAD_REQUEST, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        var details = extractFieldErrors(ex);
        log.warn("Validation error: {}", details);
        return responseFactory.of(BAD_REQUEST, VALIDATION_ERROR, details);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        var contentType = ex.getContentType() != null
                ? ex.getContentType().toString()
                : "unknown";

        var message = INVALID_FILE_FORMAT.formatted(contentType);
        log.warn("Unsupported media type: {}", contentType);
        return responseFactory.of(BAD_REQUEST, message);
    }

    // ───────────────────────── helpers ─────────────────────────

    private boolean isIntegerType(Class<?> type) {
        return type == Long.class || type == Integer.class
                || type == Short.class || type == Byte.class;
    }

    private String extractParamName(ConstraintViolation<?> violation) {
        var path = violation.getPropertyPath().toString();
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private Map<String, String> extractFieldErrors(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (a, b) -> a
                ));
    }

}
