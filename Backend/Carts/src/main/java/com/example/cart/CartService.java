package com.example.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductServiceClient productServiceClient;

    // ==================== ОПЕРАЦИИ С КОРЗИНОЙ ====================

    @Transactional
    public Cart createCart(int clientId) {
        log.info("🛒 Создание корзины для клиента: {}", clientId);

        // Проверяем, есть ли уже активная корзина
        Cart activeCart = cartRepository.findByClientIdAndStatus(clientId, CartConstants.CART_STATUS_ACTIVE);
        if (activeCart != null) {
            log.info("✅ Найдена активная корзина ID: {}", activeCart.getId());
            return activeCart;
        }

        Cart cart = new Cart(clientId, CartConstants.CART_STATUS_ACTIVE);
        Cart saved = cartRepository.save(cart);
        log.info("✅ Создана новая корзина ID: {}", saved.getId());
        return saved;
    }

    @Transactional
    public CartItem addToCart(int cartId, int productId, int quantity, double price, String warehouse) {
        log.info("➕ Добавление товара {} ({} шт.) в корзину {}, склад: {}",
                productId, quantity, cartId, warehouse);

        // Проверяем существование корзины
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Корзина не найдена: " + cartId));

        // Проверяем, что корзина активна
        if (!CartConstants.CART_STATUS_ACTIVE.equals(cart.getStatus())) {
            throw new IllegalStateException("Нельзя добавлять товары в неактивную корзину");
        }

        // Проверяем наличие через product-service с указанием склада
        try {
            Map<String, Object> availabilityCheck = productServiceClient.checkProductAvailability(
                    productId, quantity, warehouse);

            if (!Boolean.TRUE.equals(availabilityCheck.get("available"))) {
                throw new IllegalStateException("Товар недоступен в нужном количестве на складе " + warehouse);
            }

            // Резервируем товар на нужном складе
            Map<String, Object> reserveResponse = productServiceClient.reserveProduct(
                    productId, quantity, warehouse);

            if (!Boolean.TRUE.equals(reserveResponse.get("success"))) {
                throw new IllegalStateException("Не удалось зарезервировать товар на складе " + warehouse);
            }

            log.info("✅ Товар {} зарезервирован на складе {}", productId, warehouse);

        } catch (Exception e) {
            log.error("❌ Ошибка при резервировании товара: {}", e.getMessage());
            throw new RuntimeException("Ошибка при резервировании товара: " + e.getMessage());
        }

        // Проверяем, есть ли уже такой товар в корзине
        CartItem existingItem = cartItemRepository.findByCartIdAndProductId(cartId, productId);

        if (existingItem != null) {
            // Увеличиваем количество
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
            CartItem updated = cartItemRepository.save(existingItem);
            log.info("✅ Количество товара {} увеличено до {}", productId, updated.getQuantity());
            return updated;
        } else {
            // Создаем новый элемент
            CartItem newItem = new CartItem(cartId, productId, quantity, price);
            CartItem saved = cartItemRepository.save(newItem);
            log.info("✅ Товар {} добавлен в корзину", productId);
            return saved;
        }
    }

    @Transactional
    public void removeFromCart(int cartId, int itemId) {
        log.info("🗑️ Удаление товара {} из корзины {}", itemId, cartId);

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден в корзине"));

        if (item.getCartId() != cartId) {
            throw new IllegalArgumentException("Товар не принадлежит указанной корзине");
        }

        // ✅ НОВОЕ: Возвращаем товар на склад
        try {
            Map<String, Object> releaseResponse = productServiceClient.releaseProduct(
                    item.getProductId(),
                    item.getQuantity(),
                    null
            );
            if (!Boolean.TRUE.equals(releaseResponse.get("success"))) {
                log.warn("⚠️ Не удалось вернуть товар на склад, но удаляем из корзины");
            } else {
                log.info("✅ Товар {} возвращен на склад", item.getProductId());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при возврате товара: {}", e.getMessage());
            // Продолжаем удаление из корзины даже если не удалось вернуть на склад
        }

        cartItemRepository.delete(item);
        log.info("✅ Товар удален из корзины");
    }

    @Transactional
    public void clearCart(int cartId) {
        log.info("🧹 Очистка корзины {}", cartId);

        List<CartItem> items = cartItemRepository.findByCartId(cartId);

        // ✅ НОВОЕ: Возвращаем все товары на склад
        for (CartItem item : items) {
            try {
                productServiceClient.releaseProduct(item.getProductId(), item.getQuantity(), null);
                log.debug("✅ Товар {} возвращен на склад", item.getProductId());
            } catch (Exception e) {
                log.error("❌ Ошибка при возврате товара {}: {}", item.getProductId(), e.getMessage());
            }
        }

        cartItemRepository.deleteAll(items);
        log.info("✅ Корзина очищена, удалено {} товаров", items.size());
    }

    public List<Map<String, Object>> getOrderItems(int orderId) {
        log.info("🔍 Получение товаров для заказа: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден: " + orderId));

        List<CartItem> items = cartItemRepository.findByCartId(order.getCartId());

        return items.stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("productId", item.getProductId());
                    map.put("quantity", item.getQuantity());
                    map.put("price", item.getPrice());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // ==================== ОПЕРАЦИИ С ЗАКАЗАМИ ====================

    @Transactional
    public Map<String, Object> checkoutCart(int cartId, String warehouse) {
        log.info("🛍️ Service: оформление заказа для корзины {}", cartId);

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Корзина не найдена: " + cartId));

        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Нельзя оформить пустую корзину");
        }

        int clientId = cart.getClientId();

        // 1. ПРОВЕРЯЕМ НАЛИЧИЕ И РЕЗЕРВИРУЕМ ТОВАРЫ - КАК В addToCart!
        for (CartItem item : items) {
            try {
                Map<String, Object> availabilityCheck = productServiceClient.checkProductAvailability(
                        item.getProductId(),
                        item.getQuantity(),
                        warehouse
                );

                if (!Boolean.TRUE.equals(availabilityCheck.get("available"))) {
                    throw new IllegalStateException(
                            String.format("Товар %d недоступен в нужном количестве", item.getProductId())
                    );
                }

                Map<String, Object> reserveResponse = productServiceClient.reserveProduct(
                        item.getProductId(),
                        item.getQuantity(),
                        null
                );

                if (!Boolean.TRUE.equals(reserveResponse.get("success"))) {
                    throw new IllegalStateException("Не удалось зарезервировать товар");
                }

                log.info("✅ Товар {} зарезервирован на складе", item.getProductId());

            } catch (Exception e) {
                log.error("❌ Ошибка при резервировании товара {}: {}",
                        item.getProductId(), e.getMessage());

                // Откатываем уже зарезервированные
                rollbackReservations(items, item);
                throw new RuntimeException("Ошибка при резервировании товаров: " + e.getMessage());
            }
        }

        // 2. СОЗДАЕМ ЗАКАЗ
        double totalAmount = items.stream()
                .mapToDouble(item -> item.getQuantity() * item.getPrice())
                .sum();

        Order order = new Order();
        order.setCartId(cartId);
        order.setOrderNumber(generateOrderNumber());
        order.setTotalAmount(BigDecimal.valueOf(totalAmount).setScale(2, RoundingMode.HALF_UP));
        order.setStatus("PENDING");
        order.setClientId(clientId);
        order.setCreatedDate(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        cart.setStatus("processing");
        cartRepository.save(cart);

        log.info("✅ Заказ создан: {}, товары зарезервированы", savedOrder.getOrderNumber());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("orderId", savedOrder.getId());
        response.put("orderNumber", savedOrder.getOrderNumber());
        response.put("cartId", cartId);
        response.put("clientId", clientId);
        response.put("status", savedOrder.getStatus());
        response.put("totalAmount", totalAmount);
        response.put("timestamp", LocalDateTime.now());

        return response;
    }

    // Метод для отката резервирования при ошибке
    private void rollbackReservations(List<CartItem> items, CartItem failedItem) {
        log.info("🔄 Откат резервирования...");
        for (CartItem item : items) {
            if (item == failedItem) break;
            try {
                // ТОЧНО КАК В removeFromCart!
                Map<String, Object> releaseResponse = productServiceClient.releaseProduct(
                        item.getProductId(),
                        item.getQuantity(),
                        null
                );

                if (!Boolean.TRUE.equals(releaseResponse.get("success"))) {
                    log.warn("⚠️ Не удалось вернуть товар на склад");
                } else {
                    log.info("✅ Товар {} возвращен на склад", item.getProductId());
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при возврате товара {}: {}",
                        item.getProductId(), e.getMessage());
            }
        }
    }

    public List<Order> getClientOrders(int clientId) {
        log.info("📋 Получение заказов клиента: {}", clientId);
        return orderRepository.findByClientId(clientId);
    }

    public Order getOrder(int orderId) {
        log.info("🔍 Получение заказа: {}", orderId);
        log.info("🔍 Вызов репозитория для ID: {}", orderId);
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            log.info("🔍 Результат из БД: {}", orderOpt.isPresent() ? "найден" : "не найден");

            return orderOpt.orElseThrow(() -> {
                log.error("❌ Заказ не найден в БД: {}", orderId);
                return new IllegalArgumentException("Заказ не найден: " + orderId);
            });
        } catch (Exception e) {
            log.error("❌ Ошибка при обращении к БД: {}", e.getMessage(), e);
            throw e;
        }
    }

    public Order getOrderByNumber(String orderNumber) {
        log.info("🔍 Получение заказа по номеру: {}", orderNumber);
        Order order = orderRepository.findByOrderNumber(orderNumber);
        if (order == null) {
            throw new IllegalArgumentException("Заказ не найден: " + orderNumber);
        }
        return order;
    }

    @Transactional
    public Order updateOrderStatus(int orderId, String status) {
        log.info("🔄 Обновление статуса заказа {}: {}", orderId, status);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден: " + orderId));

        order.setStatus(status);
        Order updated = orderRepository.save(order);
        log.info("✅ Статус заказа обновлен");
        return updated;
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }

    private Map<String, Object> createCheckoutResponse(Cart cart, Order order,
                                                       List<CartItem> items, double totalAmount) {
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getOrderNumber());
        response.put("cartId", cart.getId());
        response.put("status", order.getStatus());
        response.put("totalAmount", totalAmount);
        response.put("message", "Заказ успешно оформлен");
        response.put("itemsCount", items.size());
        response.put("timestamp", LocalDateTime.now());
        response.put("dbOrderId", order.getId());

        // Добавляем информацию о товарах
        List<Map<String, Object>> itemsResponse = items.stream()
                .map(item -> {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("productId", item.getProductId());
                    itemMap.put("quantity", item.getQuantity());
                    itemMap.put("price", item.getPrice());
                    itemMap.put("cartItemId", item.getId());
                    return itemMap;
                })
                .collect(Collectors.toList());
        response.put("items", itemsResponse);

        return response;
    }



    //Получить полную информацию о корзинах клиента
    public Map<String, Object> getClientCartsFull(int clientId) {
        log.info("🛍️ Service: получение полной информации о корзинах клиента {}", clientId);

        // 1. Получаем корзины клиента
        List<Cart> carts = cartRepository.findByClientId(clientId);

        // 2. Получаем заказы клиента (через корзины)
        List<Integer> cartIds = carts.stream()
                .map(Cart::getId)
                .collect(Collectors.toList());

        List<Order> orders = cartIds.stream()
                .flatMap(cartId -> orderRepository.findByCartId(cartId).stream())
                .collect(Collectors.toList());

        // 3. Для каждой корзины собираем полную информацию
        List<Map<String, Object>> result = new ArrayList<>();

        for (Cart cart : carts) {
            Integer cartId = cart.getId();
            Map<String, Object> fullCart = new HashMap<>();

            // Данные корзины
            fullCart.put("id", cartId);
            fullCart.put("clientId", cart.getClientId());
            fullCart.put("createdDate", cart.getCreatedDate());

            String cartStatus = cart.getStatus();
            if (cartStatus == null || cartStatus.trim().isEmpty()) {
                cartStatus = "active";
            }

            // Ищем связанный заказ
            for (Order order : orders) {
                if (order.getCartId() == cartId) {
                    String orderStatus = order.getStatus();
                    if (orderStatus != null && !orderStatus.isEmpty()) {
                        cartStatus = orderStatus.toLowerCase();
                    }
                    fullCart.put("orderId", order.getId());
                    fullCart.put("orderNumber", order.getOrderNumber());
                    fullCart.put("orderData", Map.of(
                            "id", order.getId(),
                            "orderNumber", order.getOrderNumber(),
                            "status", order.getStatus(),
                            "totalAmount", order.getTotalAmount(),
                            "createdDate", order.getCreatedDate()
                    ));
                    break;
                }
            }

            fullCart.put("status", cartStatus);
            fullCart.put("statusSource", orders.isEmpty() ? "cart" : "order");

            // Получаем товары корзины
            List<CartItem> cartItems = cartItemRepository.findByCartId(cartId);

            List<Map<String, Object>> itemsList = new ArrayList<>();
            double cartTotal = 0.0;

            for (CartItem item : cartItems) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("id", item.getId());
                itemMap.put("productId", item.getProductId());
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("price", item.getPrice());
                itemMap.put("nalichie", item.getNalichie());

                // ✅ ДОБАВЛЯЕМ ПОЛУЧЕНИЕ НАЗВАНИЯ ТОВАРА
                String productName = "Товар #" + item.getProductId(); // запасной вариант
                try {
                    Map<String, Object> product = productServiceClient.getProduct(item.getProductId());
                    if (product != null && product.containsKey("name")) {
                        productName = (String) product.get("name");
                    }
                    log.debug("✅ Получено название для товара {}: {}", item.getProductId(), productName);
                } catch (Exception e) {
                    log.warn("⚠️ Не удалось получить название для товара {}: {}",
                            item.getProductId(), e.getMessage());
                }
                itemMap.put("productName", productName); // ← добавляем название!

                double itemTotal = item.getQuantity() * item.getPrice();
                itemMap.put("itemTotal", itemTotal);

                itemsList.add(itemMap);
                cartTotal += itemTotal;
            }

            fullCart.put("items", itemsList);
            fullCart.put("totalAmount", cartTotal);
            fullCart.put("itemsCount", itemsList.size());

            result.add(fullCart);
        }

        // 4. Формируем ответ
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("clientId", clientId);
        response.put("totalCarts", result.size());
        response.put("ordersCount", orders.size());
        response.put("carts", result);
        response.put("statusSource", orders.isEmpty() ? "cart" : "order");

        log.info("✅ Полная информация собрана для клиента {}: {} корзин, {} заказов",
                clientId, result.size(), orders.size());

        return response;
    }


    //Удаление корзины и всех связанных данных
     @Transactional
    public Map<String, Object> deleteCart(int cartId) {
        log.info("🗑️ Service: удаление корзины {}", cartId);

        // 1. Проверяем существование корзины
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Корзина не найдена: " + cartId));

        // 2. Проверяем, можно ли удалить корзину (только определенные статусы)
        String cartStatus = cart.getStatus();
        List<String> allowedCartStatuses = Arrays.asList(
                CartConstants.CART_STATUS_ACTIVE,
                CartConstants.CART_STATUS_PROCESSING,
                "created",
                "pending"
        );

        if (!allowedCartStatuses.contains(cartStatus)) {
            throw new IllegalStateException(
                    String.format("Нельзя удалить корзину со статусом '%s'. Допустимые статусы: %s",
                            cartStatus, allowedCartStatuses)
            );
        }

        // 3. Проверяем, есть ли связанные заказы
        List<Order> orders = orderRepository.findByCartId(cartId);

        // 4. Если есть заказы, проверяем их статусы
        if (!orders.isEmpty()) {
            for (Order order : orders) {
                String orderStatus = order.getStatus();
                List<String> allowedOrderStatuses = Arrays.asList(
                        CartConstants.ORDER_STATUS_CREATED,
                        "pending",
                        "created"
                );

                if (!allowedOrderStatuses.contains(orderStatus)) {
                    throw new IllegalStateException(
                            String.format("Нельзя удалить корзину, так как связанный заказ %s имеет статус '%s'",
                                    order.getOrderNumber(), orderStatus)
                    );
                }
            }
        }

        // 5. Возвращаем товары на склад
        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        for (CartItem item : items) {
            try {
                productServiceClient.releaseProduct(item.getProductId(), item.getQuantity(), null);
                log.debug("✅ Товар {} возвращен на склад", item.getProductId());
            } catch (Exception e) {
                log.error("❌ Ошибка при возврате товара {}: {}", item.getProductId(), e.getMessage());
            }
        }

        // 6. Удаляем все товары из корзины
        cartItemRepository.deleteAll(items);
        log.info("✅ Удалено {} товаров из корзины", items.size());

        // 7. Удаляем связанные заказы (если есть)
        if (!orders.isEmpty()) {
            orderRepository.deleteAll(orders);
            log.info("✅ Удалено {} связанных заказов", orders.size());
        }

        // 8. Удаляем саму корзину
        cartRepository.delete(cart);
        log.info("✅ Корзина {} успешно удалена", cartId);

        // 9. Формируем ответ
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Корзина успешно удалена");
        response.put("cartId", cartId);
        response.put("deletedItems", items.size());
        response.put("deletedOrders", orders.size());
        response.put("timestamp", LocalDateTime.now());

        return response;
    }

    public List<Map<String, Object>> getMyOrders(int clientId) {
        log.info("📦 Service: получение заказов для клиента {}", clientId);

        // 1. Получаем все корзины клиента
        List<Cart> clientCarts = cartRepository.findByClientId(clientId);

        if (clientCarts.isEmpty()) {
            log.info("Клиент {} не имеет корзин", clientId);
            return new ArrayList<>();
        }

        // 2. Получаем ID всех корзин
        List<Integer> cartIds = clientCarts.stream()
                .map(Cart::getId)
                .collect(Collectors.toList());

        // 3. Получаем заказы для этих корзин
        List<Order> orders = cartIds.stream()
                .flatMap(cartId -> orderRepository.findByCartId(cartId).stream())
                .collect(Collectors.toList());

        if (orders.isEmpty()) {
            log.info("Клиент {} не имеет заказов", clientId);
            return new ArrayList<>();
        }

        // 4. Создаем мапу корзина -> статус корзины для быстрого доступа
        Map<Integer, String> cartStatusMap = clientCarts.stream()
                .collect(Collectors.toMap(Cart::getId, Cart::getStatus));

        // 5. Формируем список заказов с дополнительной информацией
        List<Map<String, Object>> result = new ArrayList<>();

        for (Order order : orders) {
            Map<String, Object> orderMap = new HashMap<>();

            // Данные заказа
            orderMap.put("id", order.getId());
            orderMap.put("orderId", order.getId());
            orderMap.put("orderNumber", order.getOrderNumber());
            orderMap.put("cartId", order.getCartId());
            orderMap.put("status", order.getStatus());
            orderMap.put("totalAmount", order.getTotalAmount());
            orderMap.put("createdDate", order.getCreatedDate());

            // Статус корзины (для обратной совместимости)
            String cartStatus = cartStatusMap.get(order.getCartId());
            orderMap.put("cartStatus", cartStatus);

            // Добавляем флаги для фильтрации
            boolean isOrder = true;
            boolean isPaid = "PAID".equals(order.getStatus()) || "paid".equalsIgnoreCase(order.getStatus());
            boolean isProcessing = "PROCESSING".equals(order.getStatus()) || "processing".equalsIgnoreCase(order.getStatus());
            boolean isCompleted = "COMPLETED".equals(order.getStatus()) || "completed".equalsIgnoreCase(order.getStatus());
            boolean isCheckedOut = "CHECKED_OUT".equals(order.getStatus()) || "checked_out".equalsIgnoreCase(order.getStatus());

            orderMap.put("isOrder", isOrder);
            orderMap.put("isPaid", isPaid);
            orderMap.put("isProcessing", isProcessing);
            orderMap.put("isCompleted", isCompleted);
            orderMap.put("isCheckedOut", isCheckedOut);

            // Получаем товары из корзины (опционально)
            try {
                List<CartItem> items = cartItemRepository.findByCartId(order.getCartId());
                List<Map<String, Object>> itemsList = items.stream()
                        .map(item -> {
                            Map<String, Object> itemMap = new HashMap<>();
                            itemMap.put("productId", item.getProductId());
                            itemMap.put("quantity", item.getQuantity());
                            itemMap.put("price", item.getPrice());
                            itemMap.put("nalichie", item.getNalichie());
                            return itemMap;
                        })
                        .collect(Collectors.toList());
                orderMap.put("items", itemsList);
                orderMap.put("itemsCount", itemsList.size());
            } catch (Exception e) {
                log.warn("Не удалось получить товары для корзины {}: {}", order.getCartId(), e.getMessage());
                orderMap.put("items", new ArrayList<>());
                orderMap.put("itemsCount", 0);
            }

            result.add(orderMap);
        }

        // 6. Сортируем по дате создания (новые сверху)
        result.sort((a, b) -> {
            LocalDateTime dateA = (LocalDateTime) a.get("createdDate");
            LocalDateTime dateB = (LocalDateTime) b.get("createdDate");
            if (dateA == null || dateB == null) return 0;
            return dateB.compareTo(dateA);
        });

        log.info("✅ Найдено {} заказов для клиента {}", result.size(), clientId);
        return result;
    }

    @Transactional
    public Map<String, Object> completeOrder(int cartId) {
        log.info("✅ Service: завершение заказа для корзины {}", cartId);

        // 1. Проверяем существование корзины
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Корзина не найдена: " + cartId));

        // 2. Проверяем, есть ли заказ для этой корзины
        List<Order> orders = orderRepository.findByCartId(cartId);
        if (orders.isEmpty()) {
            throw new IllegalStateException("Для данной корзины не создан заказ");
        }

        // Берем последний заказ (обычно он один)
        Order order = orders.get(orders.size() - 1);

        // 3. Проверяем текущий статус заказа
        String currentStatus = order.getStatus();
        log.info("📊 Текущий статус заказа: {}", currentStatus);

        // 4. Проверяем, можно ли завершить заказ
        List<String> allowedStatuses = Arrays.asList(
                "CREATED", "PAID", "PROCESSING", "processing", "paid", "checked_out"
        );

        if (!allowedStatuses.contains(currentStatus)) {
            throw new IllegalStateException(
                    String.format("Нельзя завершить заказ со статусом '%s'. Допустимые статусы: %s",
                            currentStatus, allowedStatuses)
            );
        }

        // 5. Обновляем статус заказа на COMPLETED
        String newStatus = "COMPLETED";
        order.setStatus(newStatus);
        orderRepository.save(order);

        // 6. Обновляем статус корзины (опционально)
        cart.setStatus("completed");
        cartRepository.save(cart);

        log.info("✅ Заказ {} для корзины {} успешно завершен", order.getId(), cartId);

        // 7. Формируем ответ
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Заказ успешно завершен");
        response.put("cartId", cartId);
        response.put("orderId", order.getId());
        response.put("orderNumber", order.getOrderNumber());
        response.put("oldStatus", currentStatus);
        response.put("newStatus", newStatus);
        response.put("timestamp", LocalDateTime.now());

        // 8. Добавляем информацию о товарах (опционально)
        try {
            List<CartItem> items = cartItemRepository.findByCartId(cartId);
            List<Map<String, Object>> itemsList = items.stream()
                    .map(item -> {
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("productId", item.getProductId());
                        itemMap.put("quantity", item.getQuantity());
                        itemMap.put("price", item.getPrice());
                        return itemMap;
                    })
                    .collect(Collectors.toList());
            response.put("items", itemsList);
            response.put("itemsCount", itemsList.size());
        } catch (Exception e) {
            log.warn("Не удалось получить товары для корзины {}: {}", cartId, e.getMessage());
        }

        return response;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Map<String, Object> createOrder(Map<String, Object> orderRequest) {
        log.info("📦 Service: создание заказа с данными: {}", orderRequest);

        try {
            // 1. Проверяем обязательные поля
            Integer clientId = (Integer) orderRequest.get("clientId");
            if (clientId == null) {
                throw new IllegalArgumentException("clientId обязателен");
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) orderRequest.get("items");
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("items обязателен");
            }

            // 2. Получаем cartId из запроса
            Integer cartId = (Integer) orderRequest.get("cartId");
            if (cartId == null) {
                throw new IllegalArgumentException("cartId обязателен");
            }

            // 2.1 Получаем warehouse из запроса
            String warehouse = (String) orderRequest.get("warehouse");
            if (warehouse == null) {
                warehouse = "usersklad"; // значение по умолчанию
                log.warn("⚠️ warehouse не указан, используем usersklad");
            }

            // 3. ПОЛУЧАЕМ КОРЗИНУ
            Cart cart = cartRepository.findById(cartId)
                    .orElseThrow(() -> new IllegalArgumentException("Корзина не найдена: " + cartId));

            // 4. СОЗДАЕМ ЗАКАЗ
            String orderNumber = "ORD-" + System.currentTimeMillis();

            Object totalAmountObj = orderRequest.get("totalAmount");

            Double totalAmount;
            if (totalAmountObj instanceof Number) {
                totalAmount = ((Number) totalAmountObj).doubleValue();
            } else {
                totalAmount = Double.parseDouble(totalAmountObj.toString());
            }

            BigDecimal bdTotalAmount = BigDecimal.valueOf(totalAmount)
                    .setScale(2, RoundingMode.HALF_UP);

            Order order = new Order();
            order.setCartId(cartId);
            order.setClientId(clientId);
            order.setOrderNumber(orderNumber);
            order.setTotalAmount(bdTotalAmount);
            order.setStatus("PENDING");
            order.setCreatedDate(LocalDateTime.now());
            order.setWarehouse(warehouse); // ← сохраняем склад

            Order savedOrder = orderRepository.save(order);
            log.info("✅ Заказ создан: {}, статус: PENDING, склад: {}", savedOrder.getOrderNumber(), warehouse);

            // 5. Обновляем статус корзины
            cart.setStatus("processing");
            cartRepository.save(cart);

            // 6. ФОРМИРУЕМ ОТВЕТ
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", savedOrder.getId());
            response.put("orderId", savedOrder.getId());
            response.put("orderNumber", savedOrder.getOrderNumber());
            response.put("cartId", cartId);
            response.put("status", savedOrder.getStatus());
            response.put("clientId", clientId);
            response.put("totalAmount", totalAmount);
            response.put("items", items);
            response.put("createdDate", savedOrder.getCreatedDate());
            response.put("warehouse", warehouse); // ← возвращаем склад в ответе
            response.put("message", "Заказ создан");

            return response;

        } catch (Exception e) {
            log.error("❌ Ошибка в createOrder: {}", e.getMessage(), e);
            throw e;
        }
    }
    /**
     * Откат списания товаров при ошибке создания заказа
     */
    private void rollbackReservations(List<Map<String, Object>> processedItems) {
        if (processedItems == null || processedItems.isEmpty()) {
            return;
        }

        log.info("🔄 Возврат товаров на склад (откат) - {} товаров", processedItems.size());

        for (Map<String, Object> item : processedItems) {
            try {
                Integer productId = (Integer) item.get("productId");
                Integer quantity = (Integer) item.get("quantity");

                if (productId != null && quantity != null) {
                    Map<String, Object> releaseResponse = productServiceClient.releaseProduct(
                            productId, quantity, null
                    );

                    if (Boolean.TRUE.equals(releaseResponse.get("success"))) {
                        log.info("✅ Товар {} возвращен на склад", productId);
                    } else {
                        log.warn("⚠️ Не удалось вернуть товар {} на склад", productId);
                    }
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при возврате товара: {}", e.getMessage());
            }
        }
    }

    /**
     * Метод для подтверждения успешной оплаты
     */
    @Transactional
    public Order confirmPayment(int orderId) {
        log.info("💰 Подтверждение оплаты для заказа ID: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден: " + orderId));

        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException("Заказ не может быть оплачен в статусе: " + order.getStatus());
        }

        // Меняем статус на PAID
        order.setStatus("PAID");
        Order savedOrder = orderRepository.save(order);

        log.info("✅ Заказ {} оплачен, статус изменен на PAID. Товары остаются списанными",
                order.getOrderNumber());

        return savedOrder;
    }

    /**
     * Метод для принудительной отмены заказа (если нужно)
     */
    @Transactional
    public Order forceCancelOrder(int orderId) {
        log.info("🔄 Принудительная отмена заказа ID: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден: " + orderId));

        // Возвращаем товары на склад
        List<CartItem> items = cartItemRepository.findByCartId(order.getCartId());
        for (CartItem item : items) {
            try {
                productServiceClient.releaseProduct(item.getProductId(), item.getQuantity(), null);
                log.info("✅ Товар {} возвращен на склад", item.getProductId());
            } catch (Exception e) {
                log.error("❌ Ошибка при возврате товара: {}", e.getMessage());
            }
        }

        // Меняем статус на CANCELLED
        order.setStatus("CANCELLED");
        Order savedOrder = orderRepository.save(order);

        log.info("✅ Заказ {} отменен, статус изменен на CANCELLED, товары возвращены",
                order.getOrderNumber());

        return savedOrder;
    }

    public List<Cart> getClientCarts(int clientId) {
        log.info("📦 Получение корзин клиента: {}", clientId);
        return cartRepository.findByClientId(clientId);
    }

    public List<CartItem> getCartItems(int cartId) {
        log.info("🔍 Получение товаров корзины: {}", cartId);
        return cartItemRepository.findByCartId(cartId);
    }

    public Map<String, Object> getOrderByCartId(int cartId) {
        log.info("🔍 Поиск заказа по cart_id: {}", cartId);

        List<Order> orders = orderRepository.findByCartId(cartId);

        if (orders.isEmpty()) {
            return Map.of(
                    "success", false,
                    "message", "Заказ не найден",
                    "cartId", cartId
            );
        }

        Order order = orders.get(0);

        return Map.of(
                "success", true,
                "cartId", cartId,
                "orderId", order.getId(),
                "orderNumber", order.getOrderNumber(),
                "status", order.getStatus(),
                "totalAmount", order.getTotalAmount()
        );
    }
}