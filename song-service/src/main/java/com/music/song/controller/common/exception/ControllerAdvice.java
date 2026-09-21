package com.music.song.controller.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.music.song.advice.BaseExceptionHandler;
import com.music.song.advice.ErrorResponseFactory;
import com.music.song.dto.ErrorResponse;
import com.music.song.exception.MetadataWithIdExistException;
import com.music.song.exception.SongNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice(basePackages = "com.music.song.controller")
public class ControllerAdvice extends BaseExceptionHandler {


    public ControllerAdvice(ErrorResponseFactory responseFactory) {
        super(responseFactory);
    }

    @ExceptionHandler(SongNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(SongNotFoundException e) {
        log.warn("Resource not found. code: {}, message: {}", e.getErrorCode(), e.buildFullMessage());
        return super.handleServiceException(e);
    }

    @ExceptionHandler(MetadataWithIdExistException.class)
    public ResponseEntity<ErrorResponse> handleMetadataWithIdExistException(MetadataWithIdExistException e) {
        log.warn("Metadata with same ID already exists. code: {}, message: {}", e.getErrorCode(), e.buildFullMessage());
        return super.handleServiceException(e);
    }

}
