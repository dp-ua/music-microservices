package com.music.processor.exception;

/**
 * A synchronous call to another service failed because of a temporary problem (no connection, timeout,
 * no instance in Eureka, 5xx). Only this exception is retried.
 */
public class TransientRemoteServiceException extends RemoteServiceException {

    public TransientRemoteServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public static TransientRemoteServiceException unavailable(String service, String operation, int status,
            String body, Throwable cause) {
        return new TransientRemoteServiceException(
                "%s: cannot %s, temporary failure, status=%s, response=%s".formatted(service, operation, status, body),
                cause);
    }

}
