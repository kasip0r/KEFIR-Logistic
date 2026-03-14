// Обновленный CartServiceClient.java
package com.example.ApiGateWay;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "cart-service", url = "http://cart-service:8085", configuration = FeignConfig.class)
//@FeignClient(name = "cart-service", url = "http://localhost:8085", configuration = FeignConfig.class)
public interface CartServiceClient {

    @PostMapping("/api/cart/client/{clientId}")
    ResponseEntity<Map<String, Object>> createCart(@PathVariable("clientId") int clientId);

    @PostMapping("/api/cart/{cartId}/add")
    ResponseEntity<Map<String, Object>> addToCart(@PathVariable("cartId") int cartId,
                                                  @RequestParam("productId") int productId,
                                                  @RequestParam("quantity") int quantity,
                                                  @RequestParam("price") double price,
                                                  @RequestParam(value = "warehouse", required = false) String warehouse);

    @GetMapping("/api/cart/client/{clientId}")
    ResponseEntity<List<Map<String, Object>>> getClientCarts(@PathVariable("clientId") int clientId);

    @GetMapping("/api/cart/{cartId}/items")
    ResponseEntity<List<Map<String, Object>>> getCartItems(@PathVariable("cartId") int cartId);

    @DeleteMapping("/api/cart/{cartId}/items/{itemId}")
    ResponseEntity<Map<String, Object>> removeFromCart(@PathVariable("cartId") int cartId,
                                                       @PathVariable("itemId") int itemId);

    @DeleteMapping("/api/cart/{cartId}/clear")
    ResponseEntity<Map<String, Object>> clearCart(@PathVariable("cartId") int cartId);

    // ==================== ЗАКАЗЫ ====================

    @PostMapping("/api/cart/{cartId}/checkout")
    ResponseEntity<Map<String, Object>> checkoutCart(@PathVariable("cartId") int cartId);

    @GetMapping("/api/cart/orders/client/{clientId}")
    ResponseEntity<List<Map<String, Object>>> getClientOrders(@PathVariable("clientId") int clientId);

    @GetMapping("/api/cart/orders/{orderId}")
    ResponseEntity<Map<String, Object>> getOrder(@PathVariable("orderId") int orderId);

    @GetMapping("/api/cart/orders/number/{orderNumber}")
    ResponseEntity<Map<String, Object>> getOrderByNumber(@PathVariable("orderNumber") String orderNumber);

    @PutMapping("/api/cart/orders/{orderId}/status")
    ResponseEntity<Map<String, Object>> updateOrderStatus(@PathVariable("orderId") int orderId,
                                                          @RequestBody Map<String, Object> statusRequest);

    @GetMapping("/api/cart/client/{clientId}/full")
    ResponseEntity<Map<String, Object>> getClientCartsFull(@PathVariable("clientId") int clientId);

    @GetMapping("/api/cart/client/{clientId}/my-orders")
    ResponseEntity<List<Map<String, Object>>> getMyOrders(@PathVariable("clientId") int clientId);

    @PostMapping("/api/cart/{cartId}/complete-order")
    ResponseEntity<Map<String, Object>> completeOrder(@PathVariable("cartId") int cartId);

    @GetMapping("/api/cart/orders/all")
    ResponseEntity<List<Map<String, Object>>> getAllOrders();

    @GetMapping("/api/cart/orders/by-cart/{cartId}")
    ResponseEntity<Map<String, Object>> getOrderByCartId(@PathVariable("cartId") int cartId);

    @PostMapping("/api/cart/orders")
    ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> orderRequest);

    @DeleteMapping("/api/cart/{cartId}")
    ResponseEntity<Map<String, Object>> deleteCart(@PathVariable("cartId") int cartId);

    @GetMapping("/api/cart/health")
    ResponseEntity<Map<String, Object>> health();
}