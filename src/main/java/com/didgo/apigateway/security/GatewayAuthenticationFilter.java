package com.didgo.apigateway.security;

import com.didgo.apigateway.common.ApiErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/swagger-ui",
            "/v3/api-docs"
    );

    private final JwtTokenValidator jwtTokenValidator;
    private final ObjectMapper objectMapper;

    public GatewayAuthenticationFilter(JwtTokenValidator jwtTokenValidator, ObjectMapper objectMapper) {
        this.jwtTokenValidator = jwtTokenValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 외부 클라이언트가 내부 신뢰 헤더를 주입하더라도 모두 제거한 뒤 다시 구성한다.
        ServerHttpRequest sanitizedRequest = removeClientControlledHeaders(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        // 회원가입, 로그인, 토큰 재발급, 문서 경로는 Gateway 인증 없이 그대로 전달한다.
        if (isPublicRequest(sanitizedRequest)) {
            return chain.filter(sanitizedExchange);
        }

        String authorization = sanitizedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return writeError(sanitizedExchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid token.");
        }

        try {
            String token = authorization.substring(BEARER_PREFIX.length());
            Long userId = jwtTokenValidator.validateAccessTokenAndExtractUserId(token);

            // 내부 서비스는 외부 JWT가 아니라 Gateway가 보증한 사용자 식별 헤더만 신뢰한다.
            ServerHttpRequest authenticatedRequest = sanitizedRequest.mutate()
                    .headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                    .header(USER_ID_HEADER, String.valueOf(userId))
                    .header(REQUEST_ID_HEADER, requestId(sanitizedRequest))
                    .build();
            return chain.filter(sanitizedExchange.mutate().request(authenticatedRequest).build());
        } catch (InvalidTokenException exception) {
            return writeError(sanitizedExchange, HttpStatus.UNAUTHORIZED, exception.code(), exception.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private ServerHttpRequest removeClientControlledHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(REQUEST_ID_HEADER);
                })
                .build();
    }

    private boolean isPublicRequest(ServerHttpRequest request) {
        // 브라우저 preflight는 실제 인증 요청 이전에 먼저 오므로 토큰 없이 통과시킨다.
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return true;
        }

        String path = request.getPath().pathWithinApplication().value();
        if (request.getMethod() == HttpMethod.POST
                && ("/api/auth/signup".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/reissue".equals(path))) {
            return true;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String requestId(ServerHttpRequest request) {
        // 외부에서 전달한 요청 ID가 없으면 Gateway가 새로 만들어 하위 서비스 로그를 묶는다.
        String existingRequestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        return existingRequestId == null || existingRequestId.isBlank()
                ? UUID.randomUUID().toString()
                : existingRequestId;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        byte[] responseBody;
        try {
            responseBody = objectMapper.writeValueAsBytes(new ApiErrorResponse(code, message));
        } catch (JsonProcessingException exception) {
            // 직렬화 실패 시에도 Gateway는 최소한의 JSON 에러 응답을 직접 내려준다.
            responseBody = "{\"code\":\"GATEWAY_ERROR\",\"message\":\"Gateway error.\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(responseBody);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
