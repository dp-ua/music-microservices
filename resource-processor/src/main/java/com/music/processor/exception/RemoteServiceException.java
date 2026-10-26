package com.music.processor.exception;

/**
 * A synchronous call to another service failed and must NOT be retried (4xx, empty response).
 */
public class RemoteServiceException extends RuntimeException {

    private static final String FAILED = "%s: cannot %s, status=%s, response=%s";
    private static final String EMPTY_RESPONSE = "%s: cannot %s, empty response";

    public RemoteServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public RemoteServiceException(String message) {
        super(message);
    }

    public static RemoteServiceException failed(String service, String operation, int status, String body,
            Throwable cause) {
        return new RemoteServiceException(FAILED.formatted(service, operation, status, body), cause);
    }

    public static RemoteServiceException emptyResponse(String service, String operation) {
        return new RemoteServiceException(EMPTY_RESPONSE.formatted(service, operation));
    }

}
