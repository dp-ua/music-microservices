package com.music.resource.messaging;

/**
 * Message from the queue "resource processed", JSON: {"resourceId": 1}. Published by resource-processor
 * after the metadata of the resource is saved.
 */
public record ResourceProcessedEvent(Long resourceId) {

}
