package com.measure.community.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.HttpStatus;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceIdWebFilterTest {

    @Test
    void commitsOne32HexTraceIdHeaderAtTheOuterResponseBoundary() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"));
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange forwarded = invocation.getArgument(0);
            return forwarded.getResponse().setComplete();
        });

        filter.filter(exchange, chain).block();

        String traceId = exchange.getResponse().getHeaders().getFirst("traceId");
        assertTrue(traceId != null && traceId.matches("[0-9a-f]{32}"));
    }

    @Test
    void localUnauthorizedResponseKeepsTheOuter32HexTraceIdHeader() {
        TraceIdWebFilter traceIdFilter = new TraceIdWebFilter();
        AuthFilter authFilter = new AuthFilter("unit-test-internal-secret");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/protected"));
        GatewayFilterChain authChain = mock(GatewayFilterChain.class);
        WebFilterChain outerChain = routedExchange -> authFilter.filter(routedExchange, authChain);

        traceIdFilter.filter(exchange, outerChain).block();

        assertTrue(exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED);
        String traceId = exchange.getResponse().getHeaders().getFirst("traceId");
        assertTrue(traceId != null && traceId.matches("[0-9a-f]{32}"));
    }
}
