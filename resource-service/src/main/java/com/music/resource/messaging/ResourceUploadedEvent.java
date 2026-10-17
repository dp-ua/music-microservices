package com.music.resource.messaging;

/**
 * Message sent to the queue after a resource is uploaded. Serialized to JSON: {"resourceId": 1}.
 */
public record ResourceUploadedEvent(Long resourceId) {

}
