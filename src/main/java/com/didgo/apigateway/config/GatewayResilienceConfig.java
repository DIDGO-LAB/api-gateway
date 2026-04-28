package com.didgo.apigateway.config;

import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayResilienceConfig {

    private static final String USER_SERVICE_CIRCUIT_BREAKER = "userServiceCircuitBreaker";

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> userServiceCircuitBreakerCustomizer() {
        // YAML의 circuit breaker 설정과 별도로 TimeLimiter를 명시해 느린 하위 서비스를 빨리 끊는다.
        return factory -> factory.configure(
                builder -> builder.timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3))
                        .build()),
                USER_SERVICE_CIRCUIT_BREAKER
        );
    }
}
