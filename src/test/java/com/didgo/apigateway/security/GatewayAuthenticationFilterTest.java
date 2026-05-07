package com.didgo.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayAuthenticationFilterTest {

    private final GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter(
            new JwtTokenValidator(new JwtProperties("test-jwt-secret-key-with-at-least-32-bytes", "user-service")),
            new ObjectMapper()
    );

    @Test
    void socialVoiceWebSocketIsAuthorizedByConnectionTokenInsteadOfGatewayJwt() {
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/ws/trainings/social/voice?token=voice-token")
                .header("X-User-Id", "999")
                .build());
        GatewayFilterChain chain = chainExchange -> {
            chainCalled.set(true);
            forwardedExchange.set(chainExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(chainCalled).isTrue();
        assertThat(forwardedExchange.get().getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
    }

    @Test
    void gatewaySwaggerAndProxiedOpenApiDocsArePublic() {
        assertPublicGet("/swagger-ui.html");
        assertPublicGet("/v3/api-docs/swagger-config");
        assertPublicGet("/user-service/v3/api-docs");
        assertPublicGet("/training-service/v3/api-docs/external-training");
    }

    @Test
    void protectedTrainingApiStillRequiresBearerToken() {
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/trainings/social/sessions/10/voice/prepare")
                .build());

        filter.filter(exchange, chainExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isNotNull();
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .contains("application/json");
    }

    private void assertPublicGet(String path) {
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());

        filter.filter(exchange, chainExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
    }
}
