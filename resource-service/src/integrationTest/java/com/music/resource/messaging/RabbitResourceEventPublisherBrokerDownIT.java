package com.music.resource.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The broker goes away after the connection is established. The publisher must make all its attempts and then give
 * up quietly (@Recover): the resource is already saved, so the upload request must still succeed.
 */
@Testcontainers
@SpringBootTest(classes = RabbitPublisherTestConfig.class)
class RabbitResourceEventPublisherBrokerDownIT {

    /**
     * Retry settings of application.yml: messaging.resource-uploaded.retry.max-attempts.
     */
    private static final int MAX_ATTEMPTS = 3;

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3-management-alpine"));

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        // a dead broker must not hold the test for the default minute
        registry.add("spring.rabbitmq.connection-timeout", () -> "2s");
    }

    @Autowired
    private ResourceEventPublisher publisher;

    @MockitoSpyBean
    private RabbitTemplate rabbitTemplate;

    @Test
    @DisplayName("Stopped broker: N attempts, then the failure is only logged and no exception is thrown")
    void stoppedBrokerExhaustsAttemptsWithoutException() {
        // the connection is open and the broker works
        assertThatCode(() -> publisher.publishUploaded(1L)).doesNotThrowAnyException();
        clearInvocations(rabbitTemplate);

        RABBIT.stop();

        assertThatCode(() -> publisher.publishUploaded(2L)).doesNotThrowAnyException();

        verify(rabbitTemplate, times(MAX_ATTEMPTS))
                .convertAndSend(eq("resource.uploaded"), any(ResourceUploadedEvent.class), any(CorrelationData.class));
    }

}
