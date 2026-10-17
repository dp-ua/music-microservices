package com.music.resource.exception;

public class EventPublishException extends BaseServiceException {

    private static final String FAILED = "Message broker error: cannot publish event for resource %s: %s";

    private EventPublishException(String message, Throwable cause) {
        super("500", message, cause);
    }

    public static EventPublishException failed(Long resourceId, String reason, Throwable cause) {
        return new EventPublishException(FAILED.formatted(resourceId, reason), cause);
    }

}
