package com.music.resource.messaging;

public interface ResourceEventPublisher {

    /**
     * Sends the "resource uploaded" event with the resource id to the message broker.
     * Transient broker failures are retried. If all attempts fail, the error is logged and not rethrown:
     * the resource is already saved and the upload itself must not fail.
     */
    void publishUploaded(Long resourceId);

}
