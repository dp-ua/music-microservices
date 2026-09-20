package com.music.common.exception;

import com.music.common.enums.ServiceType;

import lombok.Getter;

@Getter
public class BaseServiceException extends RuntimeException {

    private final ServiceType service;
    private final String errorCode;

    public BaseServiceException(ServiceType service, String errorCode, String message) {
        super(message);
        this.service = service;
        this.errorCode = errorCode;
    }

    public BaseServiceException(ServiceType service, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
        this.errorCode = errorCode;
    }

    public String buildFullMessage() {
        StringBuilder sb = new StringBuilder();
        Throwable current = this;
        while (current != null) {
            if (current instanceof BaseServiceException bse) {
                sb.append("[").append(bse.getService()).append("] ");
            }
            sb.append(current.getMessage());
            current = current.getCause();
            if (current != null) {
                sb.append(" -> ");
            }
        }
        return sb.toString();
    }

}
