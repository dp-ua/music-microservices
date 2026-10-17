package com.music.resource.messaging;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import com.music.resource.exception.EventPublishException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitResourceEventPublisher implements ResourceEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    /**
     * Sends a persistent message to the queue through the default exchange and waits for the broker confirm.
     * A message that is not routed to the queue (mandatory flag) is a failure too.
     */
    @Override
    @Retryable(
            retryFor = EventPublishException.class,
            maxAttemptsExpression = "${messaging.resource-uploaded.retry.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${messaging.resource-uploaded.retry.delay-ms}",
                    multiplierExpression = "${messaging.resource-uploaded.retry.multiplier}"))
    public void publishUploaded(Long resourceId) {
        var correlation = new CorrelationData(String.valueOf(resourceId));
        try {
            rabbitTemplate.convertAndSend(
                    properties.getQueue(), new ResourceUploadedEvent(resourceId), correlation);
            var confirm = correlation.getFuture()
                    .get(properties.getConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);

            if (!confirm.isAck()) {
                throw EventPublishException.failed(resourceId, "broker nack: " + confirm.getReason(), null);
            }
            if (Objects.nonNull(correlation.getReturned())) {
                throw EventPublishException.failed(resourceId, "message was not routed to the queue", null);
            }
            log.info("Event published to queue '{}': resourceId={}", properties.getQueue(), resourceId);
        } catch (AmqpException | ExecutionException | TimeoutException e) {
            log.warn("Can't publish event for resourceId={}: {}", resourceId, e.toString());
            throw EventPublishException.failed(resourceId, e.toString(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw EventPublishException.failed(resourceId, "interrupted", e);
        }
    }

    /**
     * All attempts are exhausted. Only logs: the resource is saved, the upload must still succeed.
     */
    @Recover
    public void recover(EventPublishException e, Long resourceId) {
        log.error("Event for resourceId={} was NOT published after all attempts, "
                + "the resource will not be processed: {}", resourceId, e.getMessage(), e);
    }

}
