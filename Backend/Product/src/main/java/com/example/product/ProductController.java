package com.example.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private WarehouseScheduler warehouseScheduler;

    @Autowired
    private ProductRepository productSkladRepository;

    @Autowired
    private ProductSkladOneRepository productSkladOneRepository;

    @Autowired
    private ProductSkladTwoRepository productSkladTwoRepository;

    @Autowired
    private ProductSkladThreeRepository productSkladThreeRepository;

    // ==================== PUBLIC ENDPOINTS (доступны всем) ====================

    @GetMapping("/products")
    public ResponseEntity<?> getAllProducts() {
        log.info("📦 GET /products");
        List<Map<String, Object>> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProduct(@PathVariable Integer id) {
        log.info("🔍 GET /products/{}", id);
        Map<String, Object> product = productService.getProduct(id);

        if (product == null) {
            return notFoundResponse("Товар с id " + id + " не найден");
        }

        return ResponseEntity.ok(product);
    }

    @GetMapping("/client/products")
    public ResponseEntity<?> getProductsForClient(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("👤 GET /client/products");
        Map<String, Object> response = productService.getProductsForClient(authHeader);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/client/products/{id}")
    public ResponseEntity<?> getProductForClient(
            @PathVariable Integer id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("👤 GET /client/products/{}", id);
        Map<String, Object> response = productService.getProductForClient(id, authHeader);

        if (response == null) {
            return notFoundResponse("Товар с id " + id + " не найден");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/products/search")
    public ResponseEntity<?> searchProducts(@RequestParam String query) {
        log.info("🔍 GET /products/search?query={}", query);
        List<Map<String, Object>> products = productService.searchProducts(query);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "query", query,
                "products", products,
                "total", products.size()
        ));
    }

    @GetMapping("/products/category/{category}")
    public ResponseEntity<?> getProductsByCategory(@PathVariable String category) {
        log.info("📂 GET /products/category/{}", category);
        List<Map<String, Object>> products = productService.getProductsByCategory(category);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "category", category,
                "products", products,
                "total", products.size()
        ));
    }

    @GetMapping("/products/stats")
    public ResponseEntity<?> getProductsStats() {
        log.info("📊 GET /products/stats");
        Map<String, Object> stats = productService.getProductsStats();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "stats", stats
        ));
    }

    @GetMapping("/products/low-stock")
    public ResponseEntity<?> getLowStockProducts(
            @RequestParam(required = false, defaultValue = "10") Integer threshold) {
        log.info("⚠️ GET /products/low-stock?threshold={}", threshold);
        List<Map<String, Object>> products = productService.getLowStockProducts(threshold);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "threshold", threshold,
                "products", products,
                "total", products.size()
        ));
    }

    @GetMapping("/warehouse/status")
    public ResponseEntity<?> getWarehouseStatus() {
        log.info("📊 GET /warehouse/status");
        Map<String, Object> status = warehouseScheduler.getSchedulerStatus();
        return ResponseEntity.ok(status);
    }

    @GetMapping("/warehouse/{warehouseName}/products")
    public ResponseEntity<?> getWarehouseProducts(@PathVariable String warehouseName) {
        log.info("🏢 GET /warehouse/{}/products", warehouseName);

        // Добавляем обработку usersklad
        if ("usersklad".equals(warehouseName)) {
            List<Map<String, Object>> products = productService.getAllProducts();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "warehouse", warehouseName,
                    "products", products,
                    "total", products.size()
            ));
        }

        if (!isValidWarehouse(warehouseName)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Недопустимое название склада",
                    "allowed", List.of("skladodin", "skladdva", "skladtri")
            ));
        }

        List<Map<String, Object>> products = productService.getProductsFromWarehouseJpa(warehouseName);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "warehouse", warehouseName,
                "products", products,
                "total", products.size()
        ));
    }

    @GetMapping("/warehouse/{warehouseName}/products/{id}")
    public ResponseEntity<?> getWarehouseProduct(
            @PathVariable String warehouseName,
            @PathVariable Integer id) {
        log.info("🏢 GET /warehouse/{}/products/{}", warehouseName, id);

        if (!isValidWarehouse(warehouseName)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Недопустимое название склада"
            ));
        }

        Map<String, Object> product = productService.getProductFromWarehouseJpa(id, warehouseName);

        if (product == null) {
            return notFoundResponse("Товар не найден в складе " + warehouseName);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "warehouse", warehouseName,
                "product", product
        ));
    }

    // ==================== ADMIN ENDPOINTS (требуют роль ADMIN) ====================

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(
            @RequestBody Map<String, Object> productData,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        log.info("➕ POST /products - проверка роли: {}", role);

        if (!"admin".equalsIgnoreCase(role)) {
            log.warn("⛔ Доступ запрещен для роли: {}", role);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "error", "Доступ запрещен. Требуется роль ADMIN"
                    ));
        }

        try {
            Map<String, Object> created = productService.createProduct(productData);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Ошибка валидации: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Ошибка валидации",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Ошибка при создании товара: {}", e.getMessage());
            return errorResponse("Ошибка при создании товара", e);
        }
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> updates,  // ← здесь уже есть warehouse!
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        // Берем warehouse из тела запроса
        String warehouse = updates.containsKey("warehouse") ?
                updates.get("warehouse").toString() : null;

        log.info("✏️ PUT /products/{} - склад из тела: '{}'", id, warehouse);

        // Проверка роли ADMIN
        if (!"admin".equalsIgnoreCase(role)) {
            log.warn("⛔ Доступ запрещен для роли: {}", role);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "error", "Доступ запрещен. Требуется роль ADMIN"
                    ));
        }

        try {
            // Передаем warehouse в сервис
            Map<String, Object> updated = productService.updateProduct(id, updates, warehouse);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("не найден")) {
                return notFoundResponse(e.getMessage());
            }
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Ошибка валидации",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Ошибка при обновлении товара: {}", e.getMessage());
            return errorResponse("Ошибка при обновлении товара", e);
        }
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(
            @PathVariable Integer id,
            @RequestParam(required = false) String warehouse,  // ← добавляем @RequestParam
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        log.info("🗑️ DELETE /products/{} - проверка роли: {}, склад: {}", id, role, warehouse);

        if (!"admin".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", "Доступ запрещен"));
        }

        try {
            boolean deleted = productService.deleteProduct(id, warehouse);
            if (!deleted) {
                return notFoundResponse("Товар с id " + id + " не найден");
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Товар успешно удален"));
        } catch (Exception e) {
            log.error("Ошибка при удалении товара: {}", e.getMessage());
            return errorResponse("Ошибка при удалении товара", e);
        }
    }

    @GetMapping("/admin/products/by-warehouse")
    public ResponseEntity<?> getProductsByWarehouse(
            @RequestParam(required = false) String warehouse,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        log.info("📦 Запрос товаров для склада: {}", warehouse);

        // Проверка роли ADMIN
        if (!"admin".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", "Доступ запрещен"));
        }

        List<Map<String, Object>> products = productService.getProductsByWarehouse(warehouse);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "warehouse", warehouse != null ? warehouse : "all",
                "products", products,
                "total", products.size()
        ));
    }

    @PostMapping("/warehouse/transfer/{warehouseName}")
    public ResponseEntity<?> manualTransfer(
            @PathVariable String warehouseName,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        log.info("🔄 POST /warehouse/transfer/{} - проверка роли: {}", warehouseName, role);

        // Проверка роли ADMIN
        if (!"admin".equalsIgnoreCase(role)) {
            log.warn("⛔ Доступ запрещен для роли: {}", role);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "error", "Доступ запрещен. Требуется роль ADMIN"
                    ));
        }

        if (!isValidWarehouse(warehouseName)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Недопустимое название склада",
                    "allowed", List.of("skladodin", "skladdva", "skladtri")
            ));
        }

        warehouseScheduler.transferProductsToWarehouse(warehouseName);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Ручной перенос в " + warehouseName + " выполнен",
                "warehouse", warehouseName,
                "timestamp", java.time.LocalDateTime.now()
        ));
    }

    @PostMapping("/products/{productId}/reserve")
    public ResponseEntity<?> reserveProduct(
            @PathVariable int productId,
            @RequestParam int quantity,
            @RequestParam(required = false) String warehouse) {
        try {
            log.info("📦 Резервирование товара {} ({} шт.) на складе {}", productId, quantity, warehouse);
            Map<String, Object> result = productService.reserveProduct(productId, quantity, warehouse);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Ошибка при резервировании: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/products/{productId}/release")
    public ResponseEntity<?> releaseProduct(
            @PathVariable int productId,
            @RequestParam int quantity,
            @RequestParam(required = false) String warehouse) {
        try {
            log.info("📦 Возврат товара {} ({} шт.) на склад {}", productId, quantity, warehouse);
            Map<String, Object> result = productService.releaseProduct(productId, quantity, warehouse);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Ошибка при возврате: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/products/{productId}/check")
    public ResponseEntity<?> checkProductAvailability(
            @PathVariable int productId,
            @RequestParam int quantity,
            @RequestParam(required = false) String warehouse) {
        try {
            log.info("🔍 Проверка наличия товара {} ({} шт.) на складе {}", productId, quantity, warehouse);
            boolean available = productService.checkAvailability(productId, quantity, warehouse);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "available", available,
                    "productId", productId,
                    "quantity", quantity,
                    "warehouse", warehouse
            ));
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/products/{productId}/write-off")
    public ResponseEntity<?> writeOffProduct(
            @PathVariable int productId,
            @RequestParam int quantity,
            @RequestParam(required = false) String warehouse) {

        log.info("📦 ===== НАЧАЛО СПИСАНИЯ ТОВАРА =====");
        log.info("📦 productId: {}, quantity: {}, warehouse: {}", productId, quantity, warehouse);

        // Если склад не указан, используем usersklad
        String targetWarehouse = warehouse != null ? warehouse : "usersklad";

        try {
            // Выбираем нужный репозиторий в зависимости от склада
            Optional<?> productOpt = findProductByWarehouse(productId, targetWarehouse);

            if (productOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Товар не найден на складе " + targetWarehouse));
            }

            // Работаем с товаром через JPA
            Object product = productOpt.get();
            Integer currentCount = getProductCount(product);

            if (currentCount < quantity) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Недостаточно товара на складе",
                        "available", currentCount,
                        "requested", quantity
                ));
            }

            // Списание
            setProductCount(product, currentCount - quantity);
            Object savedProduct = saveProduct(product, targetWarehouse);
            Integer newCount = getProductCount(savedProduct);

            log.info("✅ Товар {} списан со склада {}. Было: {}, стало: {}",
                    productId, targetWarehouse, currentCount, newCount);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "productId", productId,
                    "warehouse", targetWarehouse,
                    "writtenOff", quantity,
                    "oldCount", currentCount,
                    "newCount", newCount
            ));

        } catch (Exception e) {
            log.error("❌ Ошибка при списании: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== HEALTH ====================

    @GetMapping("/products/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "product-service",
                "timestamp", System.currentTimeMillis()
        );
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private boolean isValidWarehouse(String warehouseName) {
        return List.of("skladodin", "skladdva", "skladtri").contains(warehouseName);
    }

    private ResponseEntity<Map<String, Object>> notFoundResponse(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "success", false,
                        "error", message
                ));
    }

    private ResponseEntity<Map<String, Object>> errorResponse(String error, Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "success", false,
                        "error", error,
                        "message", e.getMessage(),
                        "type", e.getClass().getSimpleName()
                ));
    }

    private Optional<?> findProductByWarehouse(int productId, String warehouse) {
        switch (warehouse) {
            case "usersklad":
                return productSkladRepository.findById(productId);
            case "skladodin":
                return productSkladOneRepository.findById(productId);
            case "skladdva":
                return productSkladTwoRepository.findById(productId);
            case "skladtri":
                return productSkladThreeRepository.findById(productId);
            default:
                return Optional.empty();
        }
    }

    private Integer getProductCount(Object product) {
        if (product instanceof ProductSklad) return ((ProductSklad) product).getCount();
        if (product instanceof ProductSkladOne) return ((ProductSkladOne) product).getCount();
        if (product instanceof ProductSkladTwo) return ((ProductSkladTwo) product).getCount();
        if (product instanceof ProductSkladThree) return ((ProductSkladThree) product).getCount();
        return 0;
    }

    private void setProductCount(Object product, int newCount) {
        if (product instanceof ProductSklad) {
            ((ProductSklad) product).setCount(newCount);
            ((ProductSklad) product).setUpdatedAt(LocalDateTime.now());
        }
        if (product instanceof ProductSkladOne) {
            ((ProductSkladOne) product).setCount(newCount);
            ((ProductSkladOne) product).setUpdatedAt(LocalDateTime.now());
        }
        if (product instanceof ProductSkladTwo) {
            ((ProductSkladTwo) product).setCount(newCount);
            ((ProductSkladTwo) product).setUpdatedAt(LocalDateTime.now());
        }
        if (product instanceof ProductSkladThree) {
            ((ProductSkladThree) product).setCount(newCount);
            ((ProductSkladThree) product).setUpdatedAt(LocalDateTime.now());
        }
    }

    private Object saveProduct(Object product, String warehouse) {
        switch (warehouse) {
            case "usersklad":
                return productSkladRepository.save((ProductSklad) product);
            case "skladodin":
                return productSkladOneRepository.save((ProductSkladOne) product);
            case "skladdva":
                return productSkladTwoRepository.save((ProductSkladTwo) product);
            case "skladtri":
                return productSkladThreeRepository.save((ProductSkladThree) product);
            default:
                throw new IllegalArgumentException("Неизвестный склад: " + warehouse);
        }
    }
}