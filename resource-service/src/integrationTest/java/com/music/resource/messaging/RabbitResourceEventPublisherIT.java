package com.music.resource.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The publisher against a real RabbitMQ: what is sent, how it is stored and what happens when the broker refuses
 * the message. A stopped broker is in {@link RabbitResourceEventPublisherBrokerDownIT}.
 */
@Testcontainers
@SpringBootTest(classes = RabbitPublisherTestConfig.class)
class RabbitResourceEventPublisherIT {

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
    }

    @Autowired
    private ResourceEventPublisher publisher;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private Queue queue;

    /**
     * A spy: the real template is used, the test only counts the publish attempts.
     */
    @MockitoSpyBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void emptyQueue() {
        // the unroutable test deletes the queue, declare it again and remove the messages of other tests
        amqpAdmin.declareQueue(queue);
        amqpAdmin.purgeQueue(queue.getName());
    }

    @Test
    @DisplayName("The message is JSON {\"resourceId\":N}")
    void messageBodyIsJson() throws IOException {
        publisher.publishUploaded(42L);

        var message = rabbitTemplate.receive(queue.getName(), 5_000);

        assertThat(message).as("message in the queue").isNotNull();
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        var mapper = new ObjectMapper();
        assertThat(mapper.readTree(new String(message.getBody(), UTF_8)))
                .isEqualTo(mapper.readTree("{\"resourceId\":42}"));
    }

    @Test
    @DisplayName("The message is persistent (delivery mode 2), the broker writes it to disk")
    void messageIsPersistent() {
        publisher.publishUploaded(7L);

        var message = rabbitTemplate.receive(queue.getName(), 5_000);

        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    @DisplayName("The queue is declared durable")
    void queueIsDurable() throws IOException, InterruptedException {
        var result = RABBIT.execInContainer("rabbitmqctl", "list_queues", "name", "durable", "--quiet");

        assertThat(result.getStdout()).containsPattern(queue.getName().replace(".", "\\.") + "\\s+true");
    }

    @Test
    @DisplayName("One message per call, sent once when the broker accepts it")
    void successfulPublishIsNotRetried() {
        publisher.publishUploaded(1L);

        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(queue.getName()), any(ResourceUploadedEvent.class), any(CorrelationData.class));
        assertThat(amqpAdmin.getQueueInfo(queue.getName()).getMessageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("A message that is not routed to a queue is a failure: all attempts are made, the call does not throw")
    void unroutableMessageIsRetriedThenRecovered() {
        amqpAdmin.deleteQueue(queue.getName());

        assertThatCode(() -> publisher.publishUploaded(5L)).doesNotThrowAnyException();

        verify(rabbitTemplate, times(MAX_ATTEMPTS))
                .convertAndSend(eq(queue.getName()), any(ResourceUploadedEvent.class), any(CorrelationData.class));
    }

}
