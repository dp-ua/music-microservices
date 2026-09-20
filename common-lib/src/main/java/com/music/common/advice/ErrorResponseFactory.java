package com.music.common.advice;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.music.common.dto.ErrorResponse;
import com.music.common.enums.ServiceType;

@Component
public class ErrorResponseFactory {

    public ResponseEntity<ErrorResponse> of(String errorCode, String message) {
        return of(errorCode, message, null, null);
    }

    public ResponseEntity<ErrorResponse> of(String errorCode, String message, Map<String, String> details) {
        return of(errorCode, message, details, null);
    }

    public ResponseEntity<ErrorResponse> of(String errorCode, String message, ServiceType service) {
        return of(errorCode, message, null, service);
    }

    public ResponseEntity<ErrorResponse> of(String errorCode, String message,
                                            Map<String, String> details, ServiceType service) {
        var response = ErrorResponse.builder()
                .errorMessage(message)
                .errorCode(errorCode)
                .details(details)
//                .service(service) // todo no need for regression run
                .build();
        return ResponseEntity.status(Integer.parseInt(errorCode)).body(response);
    }

}
