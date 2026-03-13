package com.example.ApiGateWay;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "delivery-service", url = "http://delivery-service:8083", configuration = FeignConfig.class)
//@FeignClient(name = "delivery-service", url = "http://localhost:8083", configuration = FeignConfig.class)
public interface DeliveryServiceClient {

    @PostMapping("/api/delivery")
    Object createDelivery(@RequestBody Map<String, Object> deliveryRequest);

    @PostMapping("/api/delivery/{deliveryId}/assign")
    Object assignCourier(@PathVariable Integer deliveryId,
                         @RequestBody Map<String, Object> request);

    @PostMapping("/api/delivery/{deliveryId}/status")
    Object updateDeliveryStatus(@PathVariable Integer deliveryId,
                                @RequestBody Map<String, Object> request);

    @GetMapping("/api/delivery/client/{clientId}")
    List<Object> getClientDeliveries(@PathVariable Integer clientId);

    @GetMapping("/api/delivery/courier/{courierId}")
    List<Object> getCourierDeliveries(@PathVariable Integer courierId);

    @GetMapping("/api/delivery/active")
    List<Object> getActiveDeliveries();

    @GetMapping("/api/delivery")
    List<Object> getAllDeliveries();

    @GetMapping("/api/delivery/order/{orderId}")
    List<Object> getDeliveriesByOrderId(@PathVariable Integer orderId);

    @GetMapping("/api/delivery/order/{orderId}/first")
    Object getFirstDeliveryByOrderId(@PathVariable Integer orderId);

    @PostMapping("/api/delivery/{deliveryId}/cancel")
    Object cancelDelivery(@PathVariable Integer deliveryId);

    @GetMapping("/api/delivery/{deliveryId}")
    Object getDelivery(@PathVariable Integer deliveryId);

    @GetMapping("/api/delivery/health")
    ResponseEntity<Map<String, Object>> health();


}