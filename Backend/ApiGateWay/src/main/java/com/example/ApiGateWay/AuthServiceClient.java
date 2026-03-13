package com.example.ApiGateWay;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "auth-service", url = "http://auth-service:8097", configuration = FeignConfig.class)  // для Docker
//@FeignClient(name = "auth-service", url = "http://localhost:8097", configuration = FeignConfig.class) // для локального запуска
public interface AuthServiceClient {

    @PostMapping("/api/auth/login")
    ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request);

    @PostMapping("/api/auth/logout")
    ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                               @RequestParam(value = "clientToken", required = false) String clientToken);

    @PostMapping("/api/auth/validate")
    ResponseEntity<Map<String, Object>> validateToken(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                      @RequestParam(value = "clientToken", required = false) String clientToken);

    @GetMapping("/api/auth/me")
    ResponseEntity<Map<String, Object>> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                       @RequestParam(value = "clientToken", required = false) String clientToken);

    @PostMapping("/api/auth/change-password")
    ResponseEntity<Map<String, Object>> changePassword(@RequestHeader("Authorization") String authHeader,
                                                       @RequestBody Map<String, String> request);

    @PostMapping("/api/auth/refresh-token")
    ResponseEntity<Map<String, Object>> refreshToken(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                     @RequestParam(value = "clientToken", required = false) String clientToken);

    @GetMapping("/api/auth/health")
    ResponseEntity<Map<String, Object>> health();

    // Session endpoints
    @GetMapping("/api/sessions/validate/{clientToken}")
    ResponseEntity<Map<String, Object>> validateSession(@PathVariable("clientToken") String clientToken);

    @GetMapping("/api/sessions/jwt/{clientToken}")
    ResponseEntity<Map<String, Object>> getJwtBySession(@PathVariable("clientToken") String clientToken);

    @GetMapping("/api/sessions/session/{jwtToken}")
    ResponseEntity<Map<String, Object>> getSessionByJwt(@PathVariable("jwtToken") String jwtToken);

    @PostMapping("/api/sessions/invalidate/{clientToken}")
    ResponseEntity<Map<String, Object>> invalidateSession(@PathVariable("clientToken") String clientToken);

    @PostMapping("/api/auth/extract-user-id")
    ResponseEntity<Map<String, Object>> extractUserId(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "clientToken", required = false) String clientToken);
}