package com.music.song.advice;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.music.song.dto.ErrorResponse;

@Component
public class ErrorResponseFactory {

    public ResponseEntity<ErrorResponse> of(String errorCode, String message) {
        return of(errorCode, message, null);
    }

    public ResponseEntity<ErrorResponse> of(String errorCode, String message, Map<String, String> details) {
        var response = ErrorResponse.builder()
                .errorMessage(message)
                .errorCode(errorCode)
                .details(details)
                .build();
        return ResponseEntity.status(Integer.parseInt(errorCode)).body(response);
    }

}
