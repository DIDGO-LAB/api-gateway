package com.didgo.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.jwt.secret=test-jwt-secret-key-with-at-least-32-bytes",
        "app.jwt.issuer=user-service"
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
