package com.music.processor.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.music.processor.client.dto.SongMetadataDto;
import com.music.processor.client.resource.ResourceServiceClientProxy;
import com.music.processor.client.song.SongServiceClientProxy;
import com.music.processor.exception.RemoteServiceException;
import com.music.processor.exception.TransientRemoteServiceException;

/**
 * Feign + retry against HTTP stubs. The real Feign clients, the real error translation and the real @Retryable
 * proxies are used, resource-service and song-service are WireMock servers.
 * <p>
 * Rules: connection errors and 5xx are retried (max 3 attempts), 4xx are not, 409 from song-service is a success
 * (the message was delivered twice).
 */
@SpringBootTest(
        classes = ProcessorClientsTestConfig.class,
        properties = {
                "spring.application.name=resource-processor-it",
                "eureka.client.enabled=false",
                // same count as in application.yml, a short pause to keep the test fast
                "processor.retry.max-attempts=3",
                "processor.retry.delay-ms=10",
                "processor.retry.multiplier=1"
        })
class ProcessorRetryIT {

    private static final int MAX_ATTEMPTS = 3;
    private static final String DOWNLOAD_URL = "/resources/7";
    private static final String SONGS_URL = "/songs";
    private static final String SEQUENCE = "sequence";
    private static final byte[] MP3_BYTES = {1, 2, 3, 4, 5};

    /**
     * Plain servers instead of WireMockExtension on purpose: the extension of an outer class is stopped after the
     * first @Nested class and restarted on another port, but the Spring context (and the Feign client with the old
     * port) is cached. These servers live as long as the outer class and are only reset before every test.
     */
    static final WireMockServer RESOURCE_SERVICE = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final WireMockServer SONG_SERVICE = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        RESOURCE_SERVICE.start();
        SONG_SERVICE.start();
    }

    @DynamicPropertySource
    static void neighbourServices(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.resource-service.url", RESOURCE_SERVICE::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.song-service.url", SONG_SERVICE::baseUrl);
    }

    @AfterAll
    static void stopServers() {
        RESOURCE_SERVICE.stop();
        SONG_SERVICE.stop();
    }

    @BeforeEach
    void resetServers() {
        RESOURCE_SERVICE.resetAll();
        SONG_SERVICE.resetAll();
    }

    @Autowired
    private ResourceServiceClientProxy resourceServiceClient;

    @Autowired
    private SongServiceClientProxy songServiceClient;

    @Nested
    @DisplayName("Download from resource-service")
    class Download {

        @Test
        @DisplayName("503, 503, 200: three calls and the file is returned")
        void recoversAfterTwoServerErrors() {
            stubSequence(RESOURCE_SERVICE, WireMock::get, DOWNLOAD_URL,
                    aResponse().withStatus(503),
                    aResponse().withStatus(503),
                    aResponse().withStatus(200).withHeader("Content-Type", "audio/mpeg").withBody(MP3_BYTES));

            var data = resourceServiceClient.download(7L);

            assertThat(data).isEqualTo(MP3_BYTES);
            RESOURCE_SERVICE.verify(3, getRequestedFor(urlEqualTo(DOWNLOAD_URL)));
        }

        @Test
        @DisplayName("200 at once: exactly one call")
        void successIsNotRetried() {
            RESOURCE_SERVICE.stubFor(get(DOWNLOAD_URL).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "audio/mpeg").withBody(MP3_BYTES)));

            assertThat(resourceServiceClient.download(7L)).isEqualTo(MP3_BYTES);

            RESOURCE_SERVICE.verify(1, getRequestedFor(urlEqualTo(DOWNLOAD_URL)));
        }

        @Test
        @DisplayName("503 every time: all attempts are made, then a transient error is thrown")
        void givesUpAfterMaxAttempts() {
            RESOURCE_SERVICE.stubFor(get(DOWNLOAD_URL).willReturn(aResponse().withStatus(503)));

            assertThatThrownBy(() -> resourceServiceClient.download(7L))
                    .isInstanceOf(TransientRemoteServiceException.class)
                    .hasMessageContaining("resource-service")
                    .hasMessageContaining("503");

            RESOURCE_SERVICE.verify(MAX_ATTEMPTS, getRequestedFor(urlEqualTo(DOWNLOAD_URL)));
        }

        @Test
        @DisplayName("404 (the resource does not exist): exactly one call, no retry")
        void notFoundIsNotRetried() {
            RESOURCE_SERVICE.stubFor(get(DOWNLOAD_URL).willReturn(aResponse().withStatus(404).withBody("not found")));

            assertThatThrownBy(() -> resourceServiceClient.download(7L))
                    .isInstanceOf(RemoteServiceException.class)
                    .isNotInstanceOf(TransientRemoteServiceException.class)
                    .hasMessageContaining("404");

            RESOURCE_SERVICE.verify(1, getRequestedFor(urlEqualTo(DOWNLOAD_URL)));
        }

        @Test
        @DisplayName("200 with an empty body is an error and is not retried")
        void emptyBodyIsNotRetried() {
            RESOURCE_SERVICE.stubFor(get(DOWNLOAD_URL).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "audio/mpeg")));

            assertThatThrownBy(() -> resourceServiceClient.download(7L))
                    .isInstanceOf(RemoteServiceException.class)
                    .isNotInstanceOf(TransientRemoteServiceException.class)
                    .hasMessageContaining("empty response");

            RESOURCE_SERVICE.verify(1, getRequestedFor(urlEqualTo(DOWNLOAD_URL)));
        }

    }

    @Nested
    @DisplayName("Saving metadata to song-service")
    class SaveMetadata {

        @Test
        @DisplayName("503, 503, 200: three calls with the same body and the call succeeds")
        void recoversAfterTwoServerErrors() {
            stubSequence(SONG_SERVICE, WireMock::post, SONGS_URL,
                    aResponse().withStatus(503),
                    aResponse().withStatus(503),
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"id\":7}"));

            assertThatCode(() -> songServiceClient.saveMetadata(metadata())).doesNotThrowAnyException();

            SONG_SERVICE.verify(3, postRequestedFor(urlEqualTo(SONGS_URL)).withRequestBody(equalToJson("""
                    {"id":7,"name":"Test Title","artist":"Test Artist","album":"Test Album",
                     "duration":"00:08","year":"2025"}""")));
        }

        @Test
        @DisplayName("400 (invalid metadata): exactly one call, no retry")
        void badRequestIsNotRetried() {
            SONG_SERVICE.stubFor(post(SONGS_URL).willReturn(aResponse().withStatus(400).withBody("invalid")));

            assertThatThrownBy(() -> songServiceClient.saveMetadata(metadata()))
                    .isInstanceOf(RemoteServiceException.class)
                    .isNotInstanceOf(TransientRemoteServiceException.class)
                    .hasMessageContaining("400");

            SONG_SERVICE.verify(1, postRequestedFor(urlEqualTo(SONGS_URL)));
        }

        @Test
        @DisplayName("409 (the metadata is already saved, the message came twice): success, one call")
        void conflictIsSuccess() {
            SONG_SERVICE.stubFor(post(SONGS_URL).willReturn(aResponse().withStatus(409).withBody("exists")));

            assertThatCode(() -> songServiceClient.saveMetadata(metadata())).doesNotThrowAnyException();

            SONG_SERVICE.verify(1, postRequestedFor(urlEqualTo(SONGS_URL)));
        }

        @Test
        @DisplayName("500 every time: all attempts are made, then a transient error is thrown")
        void givesUpAfterMaxAttempts() {
            SONG_SERVICE.stubFor(post(SONGS_URL).willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> songServiceClient.saveMetadata(metadata()))
                    .isInstanceOf(TransientRemoteServiceException.class)
                    .hasMessageContaining("song-service");

            SONG_SERVICE.verify(MAX_ATTEMPTS, postRequestedFor(urlEqualTo(SONGS_URL)));
        }

    }

    private static SongMetadataDto metadata() {
        return SongMetadataDto.builder()
                .id(7L)
                .name("Test Title")
                .artist("Test Artist")
                .album("Test Album")
                .duration("00:08")
                .year("2025")
                .build();
    }

    /**
     * The responses are given one by one to the same request (a WireMock scenario). After the last one the
     * scenario stays in its final state, so the last response is repeated if there are more calls.
     */
    private static void stubSequence(WireMockServer server, Function<String, MappingBuilder> request, String url,
                                     ResponseDefinitionBuilder... responses) {
        var state = Scenario.STARTED;
        for (int i = 0; i < responses.length; i++) {
            var next = "after-response-" + (i + 1);
            server.stubFor(request.apply(url)
                    .inScenario(SEQUENCE)
                    .whenScenarioStateIs(state)
                    .willSetStateTo(next)
                    .willReturn(responses[i]));
            state = next;
        }
        server.stubFor(request.apply(url)
                .inScenario(SEQUENCE)
                .whenScenarioStateIs(state)
                .willReturn(responses[responses.length - 1]));
    }

}
