package com.didgo.apigateway.fallback;

import com.didgo.apigateway.common.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/user-service")
    public ResponseEntity<ApiErrorResponse> userServiceFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("USER_SERVICE_UNAVAILABLE", "User service is unavailable."));
    }

    @RequestMapping("/fallback/training-service")
    public ResponseEntity<ApiErrorResponse> trainingServiceFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("TRAINING_SERVICE_UNAVAILABLE", "Training service is unavailable."));
    }
}
