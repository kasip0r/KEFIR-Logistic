package com.example.ApiGateWay;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", url = "${USER_SERVICE_URL:http://user-service:8081}")
//@FeignClient(name = "user-service", url = "http://user-service:8081", configuration = FeignConfig.class)
//@FeignClient(name = "client-service", url = "http://localhost:8081", configuration = FeignConfig.class)
public interface ClientServiceClient {

    // ========== ПУБЛИЧНЫЕ МЕТОДЫ ==========

    @GetMapping("/api/clients")
    ResponseEntity<List<Map<String, Object>>> getAllClients();

    @GetMapping("/api/clients/{id}")
    ResponseEntity<Map<String, Object>> getClient(@PathVariable("id") int id);

    @PostMapping("/api/clients/register")
    ResponseEntity<Map<String, Object>> registerUser(@RequestBody Map<String, Object> userData);

    @GetMapping("/api/clients/{id}/profile")
    ResponseEntity<Map<String, Object>> getClientProfilePublic(@PathVariable("id") int id);

    // ========== МЕТОДЫ ДЛЯ ПРОВЕРКИ ==========

    @PostMapping("/api/clients/check-email")
    ResponseEntity<Map<String, Object>> checkEmail(@RequestBody Map<String, String> request);

    @PostMapping("/api/clients/check-username")
    ResponseEntity<Map<String, Object>> checkUsername(@RequestBody Map<String, String> request);

    @PostMapping("/api/clients/validate")
    ResponseEntity<Map<String, Object>> validateFields(@RequestBody Map<String, String> request);

    // ========== АДМИНИСТРАТИВНЫЕ МЕТОДЫ ==========

    @PostMapping("/api/admin/clients")
    ResponseEntity<Map<String, Object>> createClientAdmin(
            @RequestBody Map<String, Object> clientData,
            @RequestHeader("Authorization") String authHeader  // ← добавить!
    );

    @GetMapping("/api/admin/clients")
    ResponseEntity<Map<String, Object>> getAllClientsAdmin(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search);

    @PutMapping("/api/admin/clients/{id}")
    ResponseEntity<Map<String, Object>> updateClientAdmin(
            @PathVariable("id") int id,
            @RequestBody Map<String, Object> updates);

    @DeleteMapping("/api/admin/clients/{id}")
    ResponseEntity<Map<String, Object>> deleteClientAdmin(@PathVariable("id") int id);

    @GetMapping("/api/clients/by-username/{username}")
    ResponseEntity<Map<String, Object>> getUserByUsername(@PathVariable("username") String username);

    // ========== HEALTH ==========

    @GetMapping("/api/clients/health")
    ResponseEntity<Map<String, Object>> health();
}