package com.measure.community.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Owns the one gateway trace ID and registers it on the original response
 * before the Gateway route commits that response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    private static final String TRACE_ID_HEADER = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String existingTraceId = exchange.getAttribute(AuthFilter.TRACE_ID_ATTRIBUTE);
        String traceId = StringUtils.hasText(existingTraceId)
                ? existingTraceId : UUID.randomUUID().toString().replace("-", "");
        if (!StringUtils.hasText(existingTraceId)) {
            exchange.getAttributes().put(AuthFilter.TRACE_ID_ATTRIBUTE, traceId);
        }

        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
