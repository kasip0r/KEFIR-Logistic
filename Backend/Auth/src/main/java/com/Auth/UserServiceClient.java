package com.Auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

//@FeignClient(name = "user-service", url = "http://localhost:8081")
@FeignClient(name = "user-service", url = "http://user-service:8081")
public interface UserServiceClient {

    @GetMapping("/api/clients/by-username/{username}")
    ResponseEntity<Map<String, Object>> getUserByUsername(@PathVariable String username);

    @GetMapping("/api/clients/{id}")
    ResponseEntity<Map<String, Object>> getUserById(@PathVariable Integer id);
}