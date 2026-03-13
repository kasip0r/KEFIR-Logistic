package com.kefir.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;

//@FeignClient(name = "auth-service", url = "http://localhost:8097")
@FeignClient(name = "auth-service", url = "http://auth-service:8097")
public interface AuthServiceClient {

    @PostMapping("/api/auth/extract-user-id")
    ResponseEntity<Map<String, Object>> extractUserId(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "clientToken", required = false) String clientToken);
}