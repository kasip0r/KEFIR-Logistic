package com.example.ApiGateWay;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "product-service", url = "http://product-service:8082", configuration = FeignConfig.class)
//@FeignClient(name = "product-service", url = "http://localhost:8082", configuration = FeignConfig.class)
public interface ProductServiceClient {

    @GetMapping("/api/products")
    ResponseEntity<List<Map<String, Object>>> getAllProducts();

    @GetMapping("/api/products/{id}")
    ResponseEntity<Map<String, Object>> getProduct(@PathVariable("id") int id);

    @PostMapping("/api/products")
    ResponseEntity<Map<String, Object>> createProduct(@RequestBody Map<String, Object> product);

    @PutMapping("/api/products/{id}")
    ResponseEntity<Map<String, Object>> updateProduct(@PathVariable("id") int id,
                                                      @RequestBody Map<String, Object> product);

    @DeleteMapping("/api/products/{id}")
    ResponseEntity<Map<String, Object>> deleteProduct(
            @PathVariable("id") int id,
            @RequestParam(value = "warehouse", required = false) String warehouse  // ← добавить!
    );

    @GetMapping("/api/client/products")
    ResponseEntity<Map<String, Object>> getProductsForClient(
            @RequestHeader("Authorization") String authHeader);

    @GetMapping("/api/client/products/{id}")
    ResponseEntity<Map<String, Object>> getProductForClient(
            @PathVariable("id") int id,
            @RequestHeader("Authorization") String authHeader);

    @GetMapping("/api/products/search")
    ResponseEntity<Map<String, Object>> searchProducts(@RequestParam("query") String query);

    @GetMapping("/api/products/category/{category}")
    ResponseEntity<Map<String, Object>> getProductsByCategory(@PathVariable("category") String category);

    @GetMapping("/api/products/stats")
    ResponseEntity<Map<String, Object>> getProductsStats();

    @GetMapping("/api/products/low-stock")
    ResponseEntity<Map<String, Object>> getLowStockProducts(
            @RequestParam(value = "threshold", required = false, defaultValue = "10") Integer threshold);

    @GetMapping("/api/warehouse/{warehouseName}/products")
    ResponseEntity<Map<String, Object>> getWarehouseProducts(@PathVariable("warehouseName") String warehouseName);

    @GetMapping("/api/warehouse/{warehouseName}/products/{id}")
    ResponseEntity<Map<String, Object>> getWarehouseProduct(
            @PathVariable("warehouseName") String warehouseName,
            @PathVariable("id") int id);

    @GetMapping("/api/warehouse/status")
    ResponseEntity<Map<String, Object>> getWarehouseStatus();

    @GetMapping("/api/admin/products/by-warehouse")
    ResponseEntity<Map<String, Object>> getProductsByWarehouse(@RequestParam("warehouse") String warehouse);

    @PostMapping("/api/warehouse/transfer/{warehouseName}")
    ResponseEntity<Map<String, Object>> manualTransfer(@PathVariable("warehouseName") String warehouseName);

    // ==================== МЕТОДЫ ДЛЯ РАБОТЫ С ЗАПАСАМИ ====================

    @PostMapping("/api/products/{productId}/reserve")
    ResponseEntity<Map<String, Object>> reserveProduct(
            @PathVariable("productId") int productId, @RequestParam("quantity") int quantity);

    @PostMapping("/api/products/{productId}/release")
    ResponseEntity<Map<String, Object>> releaseProduct(
            @PathVariable("productId") int productId,
            @RequestParam("quantity") int quantity,
            @RequestParam(value = "warehouse", required = false) String warehouse);

    @GetMapping("/api/products/{productId}/check")
    ResponseEntity<Map<String, Object>> checkProductAvailability(
            @PathVariable("productId") int productId,
            @RequestParam("quantity") int quantity,
            @RequestParam(value = "warehouse", required = false) String warehouse);

    @GetMapping("/api/products/health")
    ResponseEntity<Map<String, Object>> health();
}