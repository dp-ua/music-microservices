package com.music.processor.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.music.processor.exception.RemoteServiceException;
import com.music.processor.exception.TransientRemoteServiceException;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import feign.Response;

/**
 * The border between "retry" and "do not retry": only connection errors and 5xx are transient.
 */
class RemoteCallErrorsTest {

    private static final String SERVICE = "song-service";
    private static final String OPERATION = "save metadata 1";

    @ParameterizedTest(name = "status {0} is transient and will be retried")
    @ValueSource(ints = {500, 502, 503, 504, 599})
    void serverErrorsAreTransient(int status) {
        var error = RemoteCallErrors.translate(SERVICE, OPERATION, feignError(status, "server problem"));

        assertThat(error).isInstanceOf(TransientRemoteServiceException.class);
    }

    @ParameterizedTest(name = "status {0} is not transient and will not be retried")
    @ValueSource(ints = {400, 401, 404, 409, 422})
    void clientErrorsAreNotTransient(int status) {
        var error = RemoteCallErrors.translate(SERVICE, OPERATION, feignError(status, "bad request"));

        assertThat(error)
                .isInstanceOf(RemoteServiceException.class)
                .isNotInstanceOf(TransientRemoteServiceException.class);
    }

    @Test
    @DisplayName("A connection error (no HTTP status) is transient")
    void connectionErrorIsTransient() {
        var cause = new IOException("Connection refused");
        var connectionError = new RetryableException(
                -1, "Connection refused", Request.HttpMethod.POST, cause, (Long) null, request());

        var error = RemoteCallErrors.translate(SERVICE, OPERATION, connectionError);

        assertThat(error).isInstanceOf(TransientRemoteServiceException.class);
    }

    @Test
    @DisplayName("The message tells the service, the operation, the status and the response body")
    void messageContainsDetails() {
        var error = RemoteCallErrors.translate(SERVICE, OPERATION, feignError(400, "Validation error"));

        assertThat(error.getMessage())
                .contains(SERVICE)
                .contains(OPERATION)
                .contains("400")
                .contains("Validation error");
    }

    @Test
    @DisplayName("A very long response body is shortened")
    void longBodyIsShortened() {
        var longBody = "x".repeat(5_000);

        var error = RemoteCallErrors.translate(SERVICE, OPERATION, feignError(400, longBody));

        assertThat(error.getMessage().length()).isLessThan(1_000);
    }

    @Test
    @DisplayName("The original Feign exception is kept as the cause")
    void causeIsKept() {
        var feignError = feignError(503, "unavailable");

        var error = RemoteCallErrors.translate(SERVICE, OPERATION, feignError);

        assertThat(error).hasCause(feignError);
    }

    private static FeignException feignError(int status, String body) {
        var response = Response.builder()
                .status(status)
                .reason("reason")
                .request(request())
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("SongServiceClient#saveMetadata", response);
    }

    private static Request request() {
        return Request.create(
                Request.HttpMethod.POST, "http://song-service/songs", Map.of(), null, StandardCharsets.UTF_8, null);
    }

}
