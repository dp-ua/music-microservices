package com.music.resource.messaging;

import java.util.Objects;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.music.resource.service.ResourcesService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceProcessedListener {

    private final ResourcesService resourcesService;

    /**
     * Moves the file of the processed resource from STAGING to PERMANENT. A failure is retried by the listener
     * (spring.rabbitmq.listener.simple.retry), then the message is dropped: there is no dead-letter queue.
     */
    @RabbitListener(queues = "${messaging.resource-processed.queue}")
    public void onResourceProcessed(ResourceProcessedEvent event) {
        log.info("Received message: {}", event);
        if (Objects.isNull(event) || Objects.isNull(event.resourceId())) {
            log.error("Message without resourceId is ignored: {}", event);
            return;
        }
        resourcesService.markProcessed(event.resourceId());
    }

}
