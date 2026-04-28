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
        // 하위 서비스 상세 장애 정보는 숨기고 Gateway 공통 포맷의 503만 외부에 노출한다.
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("USER_SERVICE_UNAVAILABLE", "User service is unavailable."));
    }
}
