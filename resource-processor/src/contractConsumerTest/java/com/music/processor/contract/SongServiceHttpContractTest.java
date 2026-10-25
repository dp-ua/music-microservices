package com.music.processor.contract;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

import com.music.processor.client.dto.SongMetadataDto;
import com.music.processor.client.song.SongServiceClientProxy;
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
 * Contracts C4, C5 and C6: what resource-processor sends to POST /songs of song-service and how it reacts to
 * the answers (200, 409, 400).
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "song-service", port = ContractTestBase.SONG_SERVICE_PORT,
        pactVersion = PactSpecVersion.V3)
class SongServiceHttpContractTest extends ContractTestBase {

    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

    @Autowired
    private SongServiceClientProxy songServiceClient;

    // ---------- C4: new metadata is accepted ----------

    @Pact(consumer = "resource-processor")
    RequestResponsePact saveNewMetadata(PactDslWithProvider builder) {
        return builder
                .given("song service accepts new metadata")
                .uponReceiving("a request to save the metadata of resource 1")
                .path("/songs")
                .method("POST")
                .headers(JSON)
                .body(new PactDslJsonBody()
                        .integerType("id", 1L)
                        .stringType("name", "Test Title")
                        .stringType("artist", "Test Artist")
                        .stringType("album", "Test Album")
                        .stringMatcher("duration", "\\d{2}:[0-5]\\d", "00:08")
                        .stringMatcher("year", "(19|20)\\d{2}", "2025"))
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody().integerType("id", 1L))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "saveNewMetadata")
    void savesNewMetadata() {
        var metadata = SongMetadataDto.builder()
                .id(1L).name("Test Title").artist("Test Artist").album("Test Album")
                .duration("00:08").year("2025")
                .build();

        assertThatCode(() -> songServiceClient.saveMetadata(metadata)).doesNotThrowAnyException();
    }

    // ---------- C5: the metadata already exists ----------

    @Pact(consumer = "resource-processor")
    RequestResponsePact saveExistingMetadata(PactDslWithProvider builder) {
        return builder
                .given("metadata for resource 2 already exists")
                .uponReceiving("a request to save the metadata of resource 2 that is already saved")
                .path("/songs")
                .method("POST")
                .headers(JSON)
                .body(new PactDslJsonBody()
                        .integerType("id", 2L)
                        .stringType("name", "Test Title")
                        .stringType("artist", "Test Artist")
                        .stringType("album", "Test Album")
                        .stringMatcher("duration", "\\d{2}:[0-5]\\d", "00:08")
                        .stringMatcher("year", "(19|20)\\d{2}", "2025"))
                .willRespondWith()
                .status(409)
                .body(new PactDslJsonBody()
                        .stringType("errorMessage", "Metadata for resource ID=2 already exists")
                        .stringValue("errorCode", "409"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "saveExistingMetadata")
    void conflictMeansTheMetadataIsAlreadySaved() {
        var metadata = SongMetadataDto.builder()
                .id(2L).name("Test Title").artist("Test Artist").album("Test Album")
                .duration("00:08").year("2025")
                .build();

        // the same event was delivered twice: this is a success, not an error
        assertThatCode(() -> songServiceClient.saveMetadata(metadata)).doesNotThrowAnyException();
    }

    // ---------- C6: required fields are missing ----------

    @Pact(consumer = "resource-processor")
    RequestResponsePact saveIncompleteMetadata(PactDslWithProvider builder) {
        return builder
                .uponReceiving("a request to save metadata without the required fields")
                .path("/songs")
                .method("POST")
                .headers(JSON)
                .body(new PactDslJsonBody()
                        .integerType("id", 3L)
                        .stringType("name", "Only Title")
                        .nullValue("artist")
                        .nullValue("album")
                        .nullValue("duration")
                        .nullValue("year"))
                .willRespondWith()
                .status(400)
                .body(new PactDslJsonBody()
                        .stringType("errorMessage", "Validation error")
                        .stringValue("errorCode", "400"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "saveIncompleteMetadata")
    void validationErrorIsNotRetried() {
        var metadata = SongMetadataDto.builder().id(3L).name("Only Title").build();

        assertThatThrownBy(() -> songServiceClient.saveMetadata(metadata))
                .isInstanceOf(RemoteServiceException.class)
                .isNotInstanceOf(TransientRemoteServiceException.class)
                .hasMessageContaining("400");
    }

}
