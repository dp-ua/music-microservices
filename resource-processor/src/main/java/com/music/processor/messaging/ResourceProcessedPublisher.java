package com.music.processor.messaging;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tells resource-service (asynchronously, through the queue) that the resource is processed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceProcessedPublisher {

    private final AmqpTemplate amqpTemplate;
    private final ProcessedMessagingProperties properties;

    public void publishProcessed(Long resourceId) {
        amqpTemplate.convertAndSend(properties.getQueue(), new ResourceProcessedEvent(resourceId));
        log.info("Event published to queue '{}': resourceId={}", properties.getQueue(), resourceId);
    }

}
