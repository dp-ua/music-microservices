package com.music.processor.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.processor.messaging.ResourceUploadedEvent;
import com.music.processor.messaging.ResourceUploadedListener;
import com.music.processor.service.ResourceProcessingService;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;

/**
 * Contract C1 (messaging): the "resource uploaded" event {"resourceId": 1} that resource-processor reads from
 * the queue. The test takes the message from the pact and hands it to the real listener.
 * <p>
 * The provider name differs from the HTTP contracts ("resource-service-messaging"): a request/response pact and a
 * message pact are different files and must not be merged into one.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "resource-service-messaging", providerType = ProviderType.ASYNCH,
        pactVersion = PactSpecVersion.V3)
class ResourceUploadedMessageContractTest {

    @Pact(consumer = "resource-processor")
    MessagePact resourceUploaded(MessagePactBuilder builder) {
        return builder
                .expectsToReceive("a resource uploaded event")
                .withMetadata(Map.of("contentType", "application/json"))
                .withContent(new PactDslJsonBody().integerType("resourceId", 1L))
                .toPact(MessagePact.class);
    }

    @Test
    @PactTestFor(pactMethod = "resourceUploaded")
    void listenerProcessesTheEventFromTheContract(List<Message> messages) throws Exception {
        assertThat(messages).hasSize(1);
        var event = new ObjectMapper().readValue(messages.get(0).contentsAsBytes(), ResourceUploadedEvent.class);
        var processingService = mock(ResourceProcessingService.class);

        new ResourceUploadedListener(processingService).onResourceUploaded(event);

        verify(processingService).process(1L);
    }

}
