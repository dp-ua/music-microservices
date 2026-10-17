package com.music.processor.messaging;

/**
 * Message from the queue, JSON: {"resourceId": 1}. Published by resource-service.
 */
public record ResourceUploadedEvent(Long resourceId) {

}
