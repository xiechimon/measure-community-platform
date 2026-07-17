package com.measure.community.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网关鉴权过滤器单测:用 MockServerWebExchange 隔离测试反应式过滤器逻辑,
 * 不加载 Spring 上下文、不连 Nacos/Redis(RedisTemplate 用 Mockito 模拟)。
 */
class AuthFilterTest {

    private AuthFilter filter;
    private GatewayFilterChain chain;
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        filter = new AuthFilter();
        chain = mock(GatewayFilterChain.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        ReflectionTestUtils.setField(filter, "redisTemplate", redisTemplate);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void whiteListPath_passesThrough_andInjectsInternalAuth() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login"));

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> cap = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(cap.capture());
        assertEquals("expected-secret",
                cap.getValue().getRequest().getHeaders().getFirst("X-Internal-Auth"));
    }

    @Test
    void protectedPath_missingToken_returns401_andDoesNotForward() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/service/anything"));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        assertTrue(body.contains("请先登录"), body);
    }

    @Test
    void validToken_injectsUserInfo_andForwards() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("alibaba-token:tk1"))
                .thenReturn("{\"id\":\"42\",\"name\":\"Tom\"}");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/service/anything")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer tk1"));

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> cap = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(cap.capture());
        HttpHeaders h = cap.getValue().getRequest().getHeaders();
        assertEquals("expected-secret", h.getFirst("X-Internal-Auth"));
        String userInfoB64 = h.getFirst("X-UserInfo");
        assertNotNull(userInfoB64);
        assertTrue(new String(Base64.getDecoder().decode(userInfoB64)).contains("Tom"));
    }

    @Test
    void tokenNotInRedis_returns500() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/service/anything")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer tk-missing"));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void order_isMinus100() {
        assertEquals(-100, filter.getOrder());
    }
}
