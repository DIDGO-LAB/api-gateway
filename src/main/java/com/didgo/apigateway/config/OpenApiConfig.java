package com.didgo.apigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiGatewayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Didgo API Gateway")
                        .version("v1")
                        .description("Gateway entry point for Didgo APIs. Swagger UI aggregates downstream service OpenAPI documents.")
                        .license(new License().name("Proprietary")));
    }
}
