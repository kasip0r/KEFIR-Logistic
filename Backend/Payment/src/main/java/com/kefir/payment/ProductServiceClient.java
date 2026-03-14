package com.kefir.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

//@FeignClient(name = "product-service", url = "http://localhost:8082")
@FeignClient(name = "product-service", url = "http://product-service:8082")
public interface ProductServiceClient {

    @PostMapping("/api/products/{productId}/write-off")
    Map<String, Object> writeOffProduct(
            @PathVariable("productId") int productId,
            @RequestParam("quantity") int quantity,
            @RequestParam(value = "warehouse", required = false) String warehouse
    );

    @PostMapping("/api/products/{productId}/release")
    Map<String, Object> releaseProduct(@PathVariable("productId") int productId,
                                       @RequestParam("quantity") int quantity);

}