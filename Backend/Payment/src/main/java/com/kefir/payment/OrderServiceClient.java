package com.kefir.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

//@FeignClient(name = "cart-service", url = "http://localhost:8085")
@FeignClient(name = "cart-service", url = "http://cart-service:8085")
public interface OrderServiceClient {

    @GetMapping("/api/cart/orders/number/{orderNumber}")
    Map<String, Object> getOrderByNumber(@PathVariable("orderNumber") String orderNumber);

    @GetMapping("/api/cart/orders/{orderId}")
    Map<String, Object> getOrder(@PathVariable("orderId") int orderId);

    @GetMapping("/api/cart/orders/{orderId}/items")
    Map<String, Object> getOrderItems(@PathVariable("orderId") int orderId);

    @PutMapping("/api/cart/orders/{orderId}/status")
    Map<String, Object> updateOrderStatus(@PathVariable("orderId") int orderId,
                                          @RequestBody Map<String, Object> statusRequest);
}