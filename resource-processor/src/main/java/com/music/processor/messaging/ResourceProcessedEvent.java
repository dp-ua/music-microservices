package com.music.processor.messaging;

/**
 * Message sent to the queue "resource processed" after the metadata of the resource is saved,
 * JSON: {"resourceId": 1}. Consumed by resource-service, which moves the file from STAGING to PERMANENT.
 */
public record ResourceProcessedEvent(Long resourceId) {

}
