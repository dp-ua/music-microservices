package com.music.gateway.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.micrometer.tracing.Tracer;
import reactor.core.publisher.Mono;

/**
 * Entry point of a trace. The trace itself (trace id, span id) is created by Micrometer Tracing for every incoming
 * request and is passed to the downstream services in the {@code traceparent} header. This filter
 * <ul>
 *   <li>returns the trace id to the client in the {@code X-Trace-Id} response header, so that the logs of a request
 *       can be found by this id (Grafana -> Explore -> Elasticsearch: {@code traceId:"<id>"});</li>
 *   <li>writes the first log line of the request, so that the gateway is also visible in the trace.</li>
 * </ul>
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    private final Tracer tracer;

    public TraceIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        var span = tracer.currentSpan();
        if (span != null) {
            var traceId = span.context().traceId();
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
                return Mono.empty();
            });
        }
        log.info("Request {} {}", exchange.getRequest().getMethod(), path);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
