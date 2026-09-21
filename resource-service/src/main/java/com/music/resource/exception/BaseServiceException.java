package com.music.resource.exception;

import lombok.Getter;

@Getter
public class BaseServiceException extends RuntimeException {

    private final String errorCode;

    public BaseServiceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BaseServiceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String buildFullMessage() {
        StringBuilder sb = new StringBuilder();
        Throwable current = this;
        while (current != null) {
            sb.append(current.getMessage());
            current = current.getCause();
            if (current != null) {
                sb.append(" -> ");
            }
        }
        return sb.toString();
    }

}
