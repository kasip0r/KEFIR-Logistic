package com.example.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuthServiceClient authServiceClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductSkladOneRepository productSkladOneRepository;

    @Autowired
    private ProductSkladTwoRepository productSkladTwoRepository;

    @Autowired
    private ProductSkladThreeRepository productSkladThreeRepository;

    // ==================== ОСНОВНЫЕ CRUD ОПЕРАЦИИ ====================

    public List<Map<String, Object>> getAllProducts() {
        log.info("📦 Получение всех товаров");
        return productRepository.findAll().stream()
                .map(p -> convertToMap(p, "usersklad"))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getProduct(Integer id) {
        log.info("🔍 Получение товара ID: {}", id);
        return productRepository.findById(id)
                .map(p -> convertToMap(p, "usersklad"))
                .orElse(null);
    }

    public List<Map<String, Object>> getProductsByWarehouse(String warehouse) {
        log.info("🏢 Получение товаров для склада: {}", warehouse);

        List<Map<String, Object>> products = new ArrayList<>();

        if (warehouse == null || "all".equals(warehouse)) {
            // Получаем товары со всех складов через JPA
            products.addAll(getAllProducts()); // из usersklad
            products.addAll(getProductsFromWarehouseJpa("skladodin"));
            products.addAll(getProductsFromWarehouseJpa("skladdva"));
            products.addAll(getProductsFromWarehouseJpa("skladtri"));
        } else if ("usersklad".equals(warehouse)) {
            products = getAllProducts();
        } else {
            products = getProductsFromWarehouseJpa(warehouse);
        }

        return products;
    }

    @Transactional
    public Map<String, Object> createProduct(Map<String, Object> productData) {
        log.info("➕ Создание товара: {}", productData);

        // Получаем склад из данных
        String warehouse = productData.containsKey("warehouse") ?
                productData.get("warehouse").toString() : "usersklad";

        log.info("🏢 Создание товара на складе: {}", warehouse);

        // Валидация
        validateProductData(productData);

        // Создаем товар на нужном складе
        BaseProduct product = createProductForWarehouse(productData, warehouse);

        // Сохраняем
        BaseProduct saved = saveProduct(product, warehouse);
        log.info("✅ Товар создан с ID: {} на складе {}", saved.getId(), warehouse);

        return convertToMap(saved, warehouse);
    }



    @Transactional
    public Map<String, Object> updateProduct(Integer id, Map<String, Object> updates, String warehouse) {
        log.info("✏️ updateProduct получил warehouse: '{}'", warehouse);
        log.info("✏️ Обновление товара ID: {} на складе {}", id, warehouse);

        // Если склад не указан, используем usersklad
        String targetWarehouse = warehouse != null ? warehouse : "usersklad";

        // Находим товар в нужном складе
        BaseProduct product = findProduct(id, targetWarehouse);
        if (product == null) {
            throw new IllegalArgumentException("Товар не найден на складе " + targetWarehouse);
        }

        boolean changed = false;

        if (updates.containsKey("name") && updates.get("name") != null) {
            product.setName((String) updates.get("name"));
            changed = true;
        }

        if (updates.containsKey("price") && updates.get("price") != null) {
            product.setPrice(Double.parseDouble(updates.get("price").toString()));
            changed = true;
        }

        if (updates.containsKey("count") && updates.get("count") != null) {
            product.setCount(Integer.parseInt(updates.get("count").toString()));
            changed = true;
        }

        if (updates.containsKey("category") && updates.get("category") != null) {
            product.setCategory((String) updates.get("category"));
            changed = true;
        }

        if (updates.containsKey("akticul")) {
            product.setAkticul((String) updates.get("akticul"));
            changed = true;
        }

        if (updates.containsKey("description")) {
            product.setDescription((String) updates.get("description"));
            changed = true;
        }

        if (updates.containsKey("supplier")) {
            product.setSupplier((String) updates.get("supplier"));
            changed = true;
        }

        if (changed) {
            product.setUpdatedAt(LocalDateTime.now());
            BaseProduct saved = saveProduct(product, targetWarehouse);
            log.info("✅ Товар {} обновлен на складе {}", id, targetWarehouse);
            Map<String, Object> result = convertToMap(saved, targetWarehouse);
            result.put("warehouse", targetWarehouse);  // ← ЯВНО ДОБАВЛЯЕМ СКЛАД!
            return result;
        }

        return convertToMap(product, targetWarehouse);
    }

    @Transactional
    public boolean deleteProduct(Integer id, String warehouse) {
        log.info("🗑️ Удаление товара ID: {} со склада {}", id, warehouse);

        String targetWarehouse = warehouse != null ? warehouse : "usersklad";

        BaseProduct product = findProduct(id, targetWarehouse);
        if (product == null) {
            return false;
        }

        // Удаляем из нужного репозитория
        switch (targetWarehouse) {
            case "usersklad":
                productRepository.deleteById(id);
                break;
            case "skladodin":
                productSkladOneRepository.deleteById(id);
                break;
            case "skladdva":
                productSkladTwoRepository.deleteById(id);
                break;
            case "skladtri":
                productSkladThreeRepository.deleteById(id);
                break;
            default:
                throw new IllegalArgumentException("Неизвестный склад");
        }

        log.info("✅ Товар {} удален со склада {}", id, targetWarehouse);
        return true;
    }

    // ==================== ТОВАРЫ ДЛЯ КЛИЕНТА ====================

    public Map<String, Object> getProductsForClient(String authHeader) {
        log.info("👤 Получение товаров для клиента");

        // 1. Получаем userId из токена через AuthServiceClient
        Long userId = getUserIdFromAuthHeader(authHeader);

        // 2. Получаем склад пользователя по его ID
        String warehouse = getUserWarehouse(userId);

        // 3. Получаем товары с нужного склада
        List<Map<String, Object>> products;
        if ("usersklad".equals(warehouse)) {
            products = getAllProducts();
        } else {
            products = getProductsFromWarehouseJpa(warehouse);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("products", products);
        response.put("total", products.size());
        response.put("warehouse", warehouse);
        response.put("timestamp", new Date());

        return response;
    }

    private String getUserWarehouse(Long userId) {
        if (userId == null) return "usersklad";

        try {
            // Получаем город пользователя из БД
            String sql = "SELECT city FROM users WHERE id = ?";
            String city = jdbcTemplate.queryForObject(sql, String.class, userId);

            // Определяем склад по городу
            if (city != null && city.toLowerCase().startsWith("sklad")) {
                return city.toLowerCase();
            }
            return "usersklad";
        } catch (Exception e) {
            log.error("Ошибка получения склада пользователя {}: {}", userId, e.getMessage());
            return "usersklad";
        }
    }

    public Map<String, Object> getProductForClient(Integer id, String authHeader) {
        log.info("👤 Получение товара ID:{} для клиента", id);

        Map<String, Object> product = getProduct(id);

        if (product == null) {
            return null;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("product", product);
        response.put("warehouse", "usersklad");
        response.put("timestamp", new Date());

        return response;
    }

    // ==================== ТОВАРЫ ИЗ ЧАСТНЫХ СКЛАДОВ (JPA) ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProductsFromWarehouseJpa(String warehouseName) {
        log.info("🏢 Получение товаров из склада через JPA: {}", warehouseName);

        List<? extends BaseProduct> products;

        switch (warehouseName) {
            case "skladodin":
                products = productSkladOneRepository.findAll();
                break;
            case "skladdva":
                products = productSkladTwoRepository.findAll();
                break;
            case "skladtri":
                products = productSkladThreeRepository.findAll();
                break;
            default:
                return new ArrayList<>();
        }

        return products.stream()
                .map(p -> convertToMap(p, warehouseName))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getProductFromWarehouseJpa(Integer id, String warehouseName) {
        log.info("🏢 Получение товара {} из склада {} через JPA", id, warehouseName);

        Optional<? extends BaseProduct> productOpt;

        switch (warehouseName) {
            case "skladodin":
                productOpt = productSkladOneRepository.findById(id);
                break;
            case "skladdva":
                productOpt = productSkladTwoRepository.findById(id);
                break;
            case "skladtri":
                productOpt = productSkladThreeRepository.findById(id);
                break;
            default:
                return null;
        }

        return productOpt.map(p -> convertToMap(p, warehouseName)).orElse(null);
    }

    // ==================== ПОИСК И ФИЛЬТРАЦИЯ ====================

    public List<Map<String, Object>> searchProducts(String query) {
        log.info("🔍 Поиск товаров по запросу: {}", query);

        List<ProductSklad> products = productRepository.findByNameContainingIgnoreCase(query);

        if (products.isEmpty()) {
            // Поиск по артикулу
            Optional<ProductSklad> byAkticul = productRepository.findByAkticul(query);
            if (byAkticul.isPresent()) {
                products = Collections.singletonList(byAkticul.get());
            }
        }

        return products.stream()
                .map(p -> convertToMap(p, "usersklad"))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getProductsByCategory(String category) {
        log.info("📂 Получение товаров категории: {}", category);

        return productRepository.findByCategory(category).stream()
                .map(p -> convertToMap(p, "usersklad"))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getLowStockProducts(Integer threshold) {
        log.info("⚠️ Получение товаров с запасом менее {}", threshold);

        return productRepository.findByCountLessThan(threshold).stream()
                .map(p -> convertToMap(p, "usersklad"))
                .collect(Collectors.toList());
    }

    // ==================== ОПЕРАЦИИ СО СКЛАДАМИ ====================

    @Transactional
    public Map<String, Object> reserveProduct(int productId, int quantity, String warehouse) {
        String targetWarehouse = warehouse != null ? warehouse : "usersklad";
        log.info("📦 Резервирование товара {} ({} шт.) на складе {}", productId, quantity, targetWarehouse);

        BaseProduct product = findProduct(productId, targetWarehouse);
        if (product == null) {
            throw new IllegalArgumentException("Товар не найден на складе " + targetWarehouse);
        }

        if (product.getCount() < quantity) {
            throw new IllegalStateException(
                    String.format("Недостаточно товара на складе %s. Доступно: %d, запрошено: %d",
                            targetWarehouse, product.getCount(), quantity)
            );
        }

        product.setCount(product.getCount() - quantity);
        product.setUpdatedAt(LocalDateTime.now());

        BaseProduct saved = saveProduct(product, targetWarehouse);

        log.info("✅ Товар {} зарезервирован на складе {}. Остаток: {}",
                productId, targetWarehouse, saved.getCount());

        return Map.of(
                "success", true,
                "productId", productId,
                "reserved", quantity,
                "newCount", saved.getCount(),
                "warehouse", targetWarehouse
        );
    }

    @Transactional
    public Map<String, Object> releaseProduct(int productId, int quantity, String warehouse) {
        String targetWarehouse = warehouse != null ? warehouse : "usersklad";
        log.info("📦 Возврат товара {} ({} шт.) на склад {}", productId, quantity, targetWarehouse);

        // Находим товар в нужном складе
        BaseProduct product = findProduct(productId, targetWarehouse);
        if (product == null) {
            throw new IllegalArgumentException("Товар не найден на складе " + targetWarehouse);
        }

        // Увеличиваем количество
        product.setCount(product.getCount() + quantity);
        product.setUpdatedAt(LocalDateTime.now());

        // Сохраняем в нужном репозитории
        BaseProduct saved = saveProduct(product, targetWarehouse);

        log.info("✅ Товар {} возвращен на склад {}. Новый остаток: {}",
                productId, targetWarehouse, saved.getCount());

        return Map.of(
                "success", true,
                "productId", productId,
                "released", quantity,
                "newCount", saved.getCount(),
                "warehouse", targetWarehouse
        );
    }

    public boolean checkAvailability(int productId, int quantity, String warehouse) {
        String targetWarehouse = warehouse != null ? warehouse : "usersklad";
        log.info("🔍 Проверка наличия товара {} ({} шт.) на складе {}", productId, quantity, targetWarehouse);

        BaseProduct product = findProduct(productId, targetWarehouse);
        if (product == null) {
            return false;
        }
        return product.getCount() >= quantity;
    }

    @Transactional
    public Map<String, Object> writeOffProduct(int productId, int quantity, String warehouse) {
        String targetWarehouse = warehouse != null ? warehouse : "usersklad";
        log.info("📦 Списание товара {} ({} шт.) со склада {}", productId, quantity, targetWarehouse);

        BaseProduct product = findProduct(productId, targetWarehouse);
        if (product == null) {
            throw new IllegalArgumentException("Товар не найден на складе " + targetWarehouse);
        }

        product.setCount(product.getCount() - quantity);
        product.setUpdatedAt(LocalDateTime.now());

        BaseProduct saved = saveProduct(product, targetWarehouse);

        return Map.of(
                "success", true,
                "productId", productId,
                "writtenOff", quantity,
                "newCount", saved.getCount(),
                "warehouse", targetWarehouse
        );
    }

    // ==================== СТАТИСТИКА ====================

    public Map<String, Object> getProductsStats() {
        log.info("📊 Расчет статистики товаров");

        Map<String, Object> stats = new HashMap<>();

        long totalProducts = productRepository.count();
        stats.put("totalProducts", totalProducts);

        int totalStock = productRepository.findAll().stream()
                .mapToInt(ProductSklad::getCount)
                .sum();
        stats.put("totalStock", totalStock);

        double avgPrice = productRepository.findAll().stream()
                .mapToDouble(ProductSklad::getPrice)
                .average()
                .orElse(0.0);
        stats.put("averagePrice", avgPrice);

        Map<String, Long> categories = productRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        ProductSklad::getCategory,
                        Collectors.counting()
                ));
        stats.put("categories", categories);

        long lowStock = productRepository.findByCountLessThan(10).size();
        stats.put("lowStockProducts", lowStock);

        return stats;
    }

    // ==================== ОПЕРАЦИИ СО СКЛАДАМИ ====================

    @Transactional
    public boolean transferProductToWarehouse(Integer productId, String targetWarehouse, Integer quantity) {
        log.info("📦 Перенос товара {} в {} ({} шт.)", productId, targetWarehouse, quantity);

        // Проверяем существование товара на основном складе
        ProductSklad sourceProduct = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден на основном складе: " + productId));

        if (sourceProduct.getCount() < quantity) {
            log.error("Недостаточно товара на основном складе. Доступно: {}, нужно: {}", sourceProduct.getCount(), quantity);
            return false;
        }

        // Уменьшаем количество на основном складе
        sourceProduct.setCount(sourceProduct.getCount() - quantity);
        sourceProduct.setUpdatedAt(LocalDateTime.now());
        productRepository.save(sourceProduct);

        // Увеличиваем количество на целевом складе
        switch (targetWarehouse) {
            case "skladodin":
                ProductSkladOne targetOne = productSkladOneRepository.findById(productId)
                        .orElse(new ProductSkladOne());
                targetOne.setId(productId);
                targetOne.setName(sourceProduct.getName());
                targetOne.setPrice(sourceProduct.getPrice());
                targetOne.setCount(targetOne.getCount() + quantity);
                targetOne.setAkticul(sourceProduct.getAkticul());
                targetOne.setCategory(sourceProduct.getCategory());
                targetOne.setDescription(sourceProduct.getDescription());
                targetOne.setSupplier(sourceProduct.getSupplier());
                targetOne.setCreatedAt(sourceProduct.getCreatedAt());
                targetOne.setUpdatedAt(LocalDateTime.now());
                productSkladOneRepository.save(targetOne);
                break;
            case "skladdva":
                ProductSkladTwo targetTwo = productSkladTwoRepository.findById(productId)
                        .orElse(new ProductSkladTwo());
                targetTwo.setId(productId);
                targetTwo.setName(sourceProduct.getName());
                targetTwo.setPrice(sourceProduct.getPrice());
                targetTwo.setCount(targetTwo.getCount() + quantity);
                targetTwo.setAkticul(sourceProduct.getAkticul());
                targetTwo.setCategory(sourceProduct.getCategory());
                targetTwo.setDescription(sourceProduct.getDescription());
                targetTwo.setSupplier(sourceProduct.getSupplier());
                targetTwo.setCreatedAt(sourceProduct.getCreatedAt());
                targetTwo.setUpdatedAt(LocalDateTime.now());
                productSkladTwoRepository.save(targetTwo);
                break;
            case "skladtri":
                ProductSkladThree targetThree = productSkladThreeRepository.findById(productId)
                        .orElse(new ProductSkladThree());
                targetThree.setId(productId);
                targetThree.setName(sourceProduct.getName());
                targetThree.setPrice(sourceProduct.getPrice());
                targetThree.setCount(targetThree.getCount() + quantity);
                targetThree.setAkticul(sourceProduct.getAkticul());
                targetThree.setCategory(sourceProduct.getCategory());
                targetThree.setDescription(sourceProduct.getDescription());
                targetThree.setSupplier(sourceProduct.getSupplier());
                targetThree.setCreatedAt(sourceProduct.getCreatedAt());
                targetThree.setUpdatedAt(LocalDateTime.now());
                productSkladThreeRepository.save(targetThree);
                break;
            default:
                throw new IllegalArgumentException("Неизвестный склад: " + targetWarehouse);
        }

        log.info("✅ Товар {} успешно перенесен в {}", productId, targetWarehouse);
        return true;
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private BaseProduct createProductForWarehouse(Map<String, Object> data, String warehouse) {
        switch (warehouse) {
            case "usersklad":
                ProductSklad p1 = new ProductSklad();
                p1.setName((String) data.get("name"));
                p1.setPrice(Double.parseDouble(data.get("price").toString()));
                p1.setCount(data.containsKey("count") ?
                        Integer.parseInt(data.get("count").toString()) : 0);
                p1.setCategory((String) data.get("category"));
                p1.setAkticul((String) data.get("akticul"));
                p1.setDescription((String) data.get("description"));
                p1.setSupplier((String) data.get("supplier"));
                return p1;

            case "skladodin":
                ProductSkladOne p2 = new ProductSkladOne();
                p2.setName((String) data.get("name"));
                p2.setPrice(Double.parseDouble(data.get("price").toString()));
                p2.setCount(data.containsKey("count") ?
                        Integer.parseInt(data.get("count").toString()) : 0);
                p2.setCategory((String) data.get("category"));
                p2.setAkticul((String) data.get("akticul"));
                p2.setDescription((String) data.get("description"));
                p2.setSupplier((String) data.get("supplier"));
                return p2;

            case "skladdva":
                ProductSkladTwo p3 = new ProductSkladTwo();
                p3.setName((String) data.get("name"));
                p3.setPrice(Double.parseDouble(data.get("price").toString()));
                p3.setCount(data.containsKey("count") ?
                        Integer.parseInt(data.get("count").toString()) : 0);
                p3.setCategory((String) data.get("category"));
                p3.setAkticul((String) data.get("akticul"));
                p3.setDescription((String) data.get("description"));
                p3.setSupplier((String) data.get("supplier"));
                return p3;

            case "skladtri":
                ProductSkladThree p4 = new ProductSkladThree();
                p4.setName((String) data.get("name"));
                p4.setPrice(Double.parseDouble(data.get("price").toString()));
                p4.setCount(data.containsKey("count") ?
                        Integer.parseInt(data.get("count").toString()) : 0);
                p4.setCategory((String) data.get("category"));
                p4.setAkticul((String) data.get("akticul"));
                p4.setDescription((String) data.get("description"));
                p4.setSupplier((String) data.get("supplier"));
                return p4;

            default:
                throw new IllegalArgumentException("Неизвестный склад: " + warehouse);
        }
    }

    private BaseProduct findProduct(Integer id, String warehouse) {
        switch (warehouse) {
            case "usersklad": return productRepository.findById(id).orElse(null);
            case "skladodin": return productSkladOneRepository.findById(id).orElse(null);
            case "skladdva": return productSkladTwoRepository.findById(id).orElse(null);
            case "skladtri": return productSkladThreeRepository.findById(id).orElse(null);
            default: return null;
        }
    }

    private BaseProduct saveProduct(BaseProduct product, String warehouse) {
        log.info("💾 Сохранение товара ID: {} на склад: {}, тип: {}",
                product.getId(), warehouse, product.getClass().getSimpleName());

        switch (warehouse) {
            case "usersklad":
                ProductSklad p1 = (ProductSklad) product;
                log.info("   → usersklad, count: {}", p1.getCount());
                return productRepository.save(p1);
            case "skladodin":
                ProductSkladOne p2 = (ProductSkladOne) product;
                log.info("   → skladodin, count: {}", p2.getCount());
                return productSkladOneRepository.save(p2);
            case "skladdva":
                ProductSkladTwo p3 = (ProductSkladTwo) product;
                log.info("   → skladdva, count: {}", p3.getCount());
                return productSkladTwoRepository.save(p3);
            case "skladtri":
                ProductSkladThree p4 = (ProductSkladThree) product;
                log.info("   → skladtri, count: {}", p4.getCount());
                return productSkladThreeRepository.save(p4);
            default: throw new IllegalArgumentException("Неизвестный склад");
        }
    }

    private Map<String, Object> convertToMap(BaseProduct product, String warehouse) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", product.getId());
        map.put("name", product.getName());
        map.put("price", product.getPrice());
        map.put("count", product.getCount());
        map.put("akticul", product.getAkticul() != null ? product.getAkticul() : "");
        map.put("category", product.getCategory());
        map.put("description", product.getDescription() != null ? product.getDescription() : "");
        map.put("supplier", product.getSupplier() != null ? product.getSupplier() : "");
        map.put("createdAt", product.getCreatedAt());
        map.put("updatedAt", product.getUpdatedAt());
        map.put("warehouse", warehouse);
        return map;
    }

    private void validateProductData(Map<String, Object> data) {
        List<String> errors = new ArrayList<>();

        if (!data.containsKey("name") || data.get("name") == null) {
            errors.add("Название обязательно");
        }

        if (!data.containsKey("price") || data.get("price") == null) {
            errors.add("Цена обязательна");
        } else {
            try {
                double price = Double.parseDouble(data.get("price").toString());
                if (price <= 0) {
                    errors.add("Цена должна быть положительной");
                }
            } catch (NumberFormatException e) {
                errors.add("Цена должна быть числом");
            }
        }

        if (!data.containsKey("category") || data.get("category") == null) {
            errors.add("Категория обязательна");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }

    private Long getUserIdFromAuthHeader(String authHeader) {
        try {
            if (authHeader == null) return null;

            ResponseEntity<Map<String, Object>> response = authServiceClient.extractUserId(authHeader, null);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object userIdObj = response.getBody().get("userId");
                if (userIdObj instanceof Number) {
                    return ((Number) userIdObj).longValue();
                } else if (userIdObj instanceof String) {
                    return Long.parseLong((String) userIdObj);
                }
            }
            return null;
        } catch (Exception e) {
            log.error("❌ Ошибка извлечения userId: {}", e.getMessage());
            return null;
        }
    }

    private String getUserCity(Long userId) {
        if (userId == null) return null;

        try {
            String sql = "SELECT city FROM users WHERE id = ?";
            return jdbcTemplate.queryForObject(sql, String.class, userId);
        } catch (Exception e) {
            log.error("Ошибка получения города пользователя {}: {}", userId, e.getMessage());
            return null;
        }
    }
}