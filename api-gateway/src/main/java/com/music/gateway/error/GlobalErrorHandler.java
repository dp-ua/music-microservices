package com.music.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Global error handler of the API Gateway: converts any routing/proxying failure
 * into a user-friendly JSON response (service down, unknown route, timeout, ...).
 */
@Component
@Order(-2) // before Spring Boot's DefaultErrorWebExceptionHandler (-1)
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatusCode status = resolveStatus(ex);
        String message = resolveMessage(status, ex);
        String path = exchange.getRequest().getPath().value();
        log.warn("Gateway error on {} {}: {} ({})", exchange.getRequest().getMethod(), path,
                ex.getClass().getSimpleName(), ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status instanceof HttpStatus hs ? hs.getReasonPhrase() : "Error");
        body.put("message", message);
        body.put("path", path);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            DataBuffer buffer = exchange.getResponse().bufferFactory()
                    .wrap(objectMapper.writeValueAsBytes(body));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private HttpStatusCode resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return rse.getStatusCode();
        }
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConnectException) return HttpStatus.SERVICE_UNAVAILABLE;
            if (t instanceof TimeoutException) return HttpStatus.GATEWAY_TIMEOUT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveMessage(HttpStatusCode status, Throwable ex) {
        int code = status.value();
        if (code == 404) return "The requested resource was not found. Please check the URL.";
        if (code == 503) return "The requested service is temporarily unavailable. Please try again later.";
        if (code == 504) return "The service took too long to respond. Please try again later.";
        if (code == 502) return "Received an invalid response from the service. Please try again later.";
        if (code >= 400 && code < 500) return "The request could not be processed.";
        return "An unexpected error occurred. Please try again later.";
    }
}
