package com.example.ApiGateWay;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "office-service", url = "http://office-service:8086", configuration = FeignConfig.class)
//@FeignClient(name = "office-service", url = "http://localhost:8086", configuration = FeignConfig.class)
public interface OfficeServiceClient {

    @PostMapping("/api/office/accept-return-from-collector")
    Map<String, Object> acceptReturnFromCollector(@RequestBody Map<String, Object> returnRequest);

    @PostMapping("/api/office/give-return-to-client")
    Map<String, Object> giveReturnToClient(@RequestBody Map<String, Object> returnRequest);

    @PostMapping("/api/office/send-return-to-collector")
    Map<String, Object> sendReturnToCollector(@RequestBody Map<String, Object> returnRequest);

    @GetMapping("/api/office/returns")
    List<Map<String, Object>> getAllReturns();

    @GetMapping("/api/office/returns/{id}")
    Map<String, Object> getReturnById(@PathVariable("id") Long id);

    @GetMapping("/api/office/returns/client/{clientId}")
    List<Map<String, Object>> getReturnsByClientId(@PathVariable("clientId") String clientId);

    @GetMapping("/api/office/health")
    ResponseEntity<Map<String, Object>> health();
}