package com.music.resource.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import com.music.resource.messaging.MessagingProperties;
import com.music.resource.messaging.RabbitResourceEventPublisher;

import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;

/**
 * resource-service as a message provider: the contract of the "resource uploaded" event. The real
 * {@link RabbitResourceEventPublisher} publishes an event, the message that it would send to the broker is
 * built by the same Jackson converter as in production and is compared with the pact of resource-processor.
 * <p>
 * No Spring context and no broker: RabbitTemplate is a mock that catches the event and confirms it at once
 * (otherwise the publisher would wait for the broker confirm).
 */
@Provider("resource-service-messaging")
@PactFolder("../build/pacts-shared")
class ResourceUploadedMessagePactVerificationTest {

    @BeforeEach
    void setTarget(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget());
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @PactVerifyProvider("a resource uploaded event")
    MessageAndMetadata resourceUploadedEvent() {
        var sentEvent = new AtomicReference<Object>();
        var rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            sentEvent.set(invocation.getArgument(1));
            // the broker confirm that the publisher waits for
            ((CorrelationData) invocation.getArgument(2)).getFuture()
                    .complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), any(Object.class), any(CorrelationData.class));

        var properties = new MessagingProperties();
        properties.setQueue("resource.uploaded");
        properties.setConfirmTimeout(Duration.ofSeconds(2));

        new RabbitResourceEventPublisher(rabbitTemplate, properties).publishUploaded(1L);

        // the same conversion as in RabbitConfig.jsonMessageConverter()
        var message = new Jackson2JsonMessageConverter().toMessage(sentEvent.get(), new MessageProperties());
        return new MessageAndMetadata(message.getBody(), Map.of("contentType", "application/json"));
    }

}
