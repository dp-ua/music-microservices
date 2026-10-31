package com.music.processor.contract;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

import com.music.processor.client.resource.ResourceServiceClientProxy;
import com.music.processor.exception.RemoteServiceException;
import com.music.processor.exception.TransientRemoteServiceException;

import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;

/**
 * Contracts C2 and C3: what resource-processor expects from GET /resources/{id} of resource-service.
 * The test calls the real Feign client against a Pact mock server and writes the pact file.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "resource-service", port = ContractTestBase.RESOURCE_SERVICE_PORT,
        pactVersion = PactSpecVersion.V3)
class ResourceServiceHttpContractTest extends ContractTestBase {

    private static final byte[] MP3 = "ID3-fake-mp3-content".getBytes(UTF_8);
    private static final Map<String, String> ACCEPT_AUDIO = Map.of("Accept", "audio/mpeg");

    @Autowired
    private ResourceServiceClientProxy resourceServiceClient;

    // ---------- C2: the file of an existing resource ----------

    @Pact(consumer = "resource-processor")
    RequestResponsePact downloadExistingResource(PactDslWithProvider builder) {
        return builder
                .given("resource 1 exists")
                .uponReceiving("a request for the file of resource 1")
                .path("/resources/1")
                .method("GET")
                .headers(ACCEPT_AUDIO)
                .willRespondWith()
                .status(200)
                .body(new String(MP3, UTF_8), "audio/mpeg")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "downloadExistingResource")
    void downloadsTheFileOfAnExistingResource() {
        assertThat(resourceServiceClient.download(1L)).isEqualTo(MP3);
    }

    // ---------- C3: a resource that does not exist ----------

    @Pact(consumer = "resource-processor")
    RequestResponsePact downloadUnknownResource(PactDslWithProvider builder) {
        return builder
                .given("resource 999 does not exist")
                .uponReceiving("a request for the file of resource 999")
                .path("/resources/999")
                .method("GET")
                .headers(ACCEPT_AUDIO)
                .willRespondWith()
                .status(404)
                .body(new PactDslJsonBody()
                        .stringType("errorMessage", "Resource with ID=999 not found")
                        .stringValue("errorCode", "404"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "downloadUnknownResource")
    void unknownResourceIsAnErrorThatIsNotRetried() {
        assertThatThrownBy(() -> resourceServiceClient.download(999L))
                .isInstanceOf(RemoteServiceException.class)
                .isNotInstanceOf(TransientRemoteServiceException.class)
                .hasMessageContaining("404");
    }

}
