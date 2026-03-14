package com.example.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    // ==================== КОРЗИНЫ ====================

    @PostMapping("/client/{clientId}")
    public ResponseEntity<?> createCart(@PathVariable int clientId) {
        try {
            log.info("🛒 POST /cart/client/{}", clientId);
            Cart cart = cartService.createCart(clientId);
            return ResponseEntity.ok(cart);
        } catch (Exception e) {
            log.error("Ошибка при создании корзины: {}", e.getMessage());
            return errorResponse("Ошибка при создании корзины", e);
        }
    }

    @PostMapping("/{cartId}/add")
    public ResponseEntity<?> addToCart(@PathVariable int cartId,
                                       @RequestParam int productId,
                                       @RequestParam int quantity,
                                       @RequestParam double price,
                                       @RequestParam(required = false) String warehouse) {
        try {
            log.info("➕ POST /cart/{}/add - productId: {}, quantity: {}, warehouse: {}",
                    cartId, productId, quantity, warehouse);
            CartItem item = cartService.addToCart(cartId, productId, quantity, price, warehouse);
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            return badRequestResponse(e.getMessage());
        } catch (IllegalStateException e) {
            return badRequestResponse(e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при добавлении товара: {}", e.getMessage());
            return errorResponse("Ошибка при добавлении товара", e);
        }
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<?> getClientCarts(@PathVariable int clientId) {
        try {
            log.info("📦 GET /cart/client/{}", clientId);
            List<Cart> carts = cartService.getClientCarts(clientId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "clientId", clientId,
                    "carts", carts,
                    "total", carts.size()
            ));
        } catch (Exception e) {
            log.error("Ошибка при получении корзин: {}", e.getMessage());
            return errorResponse("Ошибка при получении корзин", e);
        }
    }

    @GetMapping("/{cartId}/items")
    public ResponseEntity<?> getCartItems(@PathVariable int cartId) {
        try {
            log.info("🔍 GET /cart/{}/items", cartId);
            List<CartItem> items = cartService.getCartItems(cartId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "cartId", cartId,
                    "items", items,
                    "total", items.size()
            ));
        } catch (Exception e) {
            log.error("Ошибка при получении товаров корзины: {}", e.getMessage());
            return errorResponse("Ошибка при получении товаров", e);
        }
    }

    @DeleteMapping("/{cartId}/items/{itemId}")
    public ResponseEntity<?> removeFromCart(@PathVariable int cartId, @PathVariable int itemId) {
        try {
            log.info("🗑️ DELETE /cart/{}/items/{}", cartId, itemId);
            cartService.removeFromCart(cartId, itemId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Товар удален из корзины"
            ));
        } catch (IllegalArgumentException e) {
            return notFoundResponse(e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при удалении товара: {}", e.getMessage());
            return errorResponse("Ошибка при удалении товара", e);
        }
    }

    @DeleteMapping("/{cartId}/clear")
    public ResponseEntity<?> clearCart(@PathVariable int cartId) {
        try {
            log.info("🧹 DELETE /cart/{}/clear", cartId);
            cartService.clearCart(cartId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Корзина очищена"
            ));
        } catch (Exception e) {
            log.error("Ошибка при очистке корзины: {}", e.getMessage());
            return errorResponse("Ошибка при очистке корзины", e);
        }
    }

    @DeleteMapping("/{cartId}")
    public ResponseEntity<?> deleteCart(@PathVariable int cartId) {
        try {
            log.info("🗑️ DELETE /api/cart/{} - запрос на удаление корзины", cartId);

            Map<String, Object> result = cartService.deleteCart(cartId);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Корзина не найдена: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "error", "Корзина не найдена",
                            "message", e.getMessage()
                    ));
        } catch (IllegalStateException e) {
            log.warn("⚠️ Невозможно удалить корзину: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "error", "Невозможно удалить корзину",
                            "message", e.getMessage()
                    ));
        } catch (Exception e) {
            log.error("❌ Ошибка при удалении корзины: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Ошибка при удалении корзины",
                            "message", e.getMessage()
                    ));
        }
    }

    // ==================== ЗАКАЗЫ ====================

    @PostMapping("/{cartId}/checkout")
    public ResponseEntity<?> checkoutCart(@PathVariable int cartId, String warehouse) {
        try {
            log.info("🛍️ POST /cart/{}/checkout", cartId);
            Map<String, Object> response = cartService.checkoutCart(cartId,warehouse);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Ошибка при оформлении заказа: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/orders/client/{clientId}")
    public ResponseEntity<?> getClientOrders(@PathVariable int clientId) {
        try {
            log.info("📋 GET /orders/client/{}", clientId);
            List<Order> orders = cartService.getClientOrders(clientId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "clientId", clientId,
                    "orders", orders,
                    "total", orders.size()
            ));
        } catch (Exception e) {
            log.error("Ошибка при получении заказов: {}", e.getMessage());
            return errorResponse("Ошибка при получении заказов", e);
        }
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable int orderId) {
        try {
            log.info("🔍 GET /orders/{}", orderId);
            Order order = cartService.getOrder(orderId);
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException e) {
            return notFoundResponse(e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при получении заказа: {}", e.getMessage());
            return errorResponse("Ошибка при получении заказа", e);
        }
    }

    @GetMapping("/orders/by-cart/{cartId}")
    public ResponseEntity<?> getOrderByCartId(@PathVariable int cartId) {
        try {
            log.info("🔍 GET /orders/by-cart/{}", cartId);
            Map<String, Object> response = cartService.getOrderByCartId(cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Ошибка: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/orders/{orderId}/items")
    public ResponseEntity<?> getOrderItems(@PathVariable int orderId) {
        try {
            log.info("🔍 GET /orders/{}/items", orderId);
            List<Map<String, Object>> items = cartService.getOrderItems(orderId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "orderId", orderId,
                    "items", items,
                    "total", items.size()
            ));
        } catch (Exception e) {
            log.error("❌ Ошибка при получении товаров заказа: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/orders/number/{orderNumber}")
    public ResponseEntity<?> getOrderByNumber(@PathVariable String orderNumber) {
        try {
            log.info("🔍 GET /orders/number/{}", orderNumber);

            // Получаем заказ
            Order order = cartService.getOrderByNumber(orderNumber);

            // Получаем товары из корзины
            List<CartItem> items = cartService.getCartItems(order.getCartId());

            // Преобразуем товары в список Map
            List<Map<String, Object>> itemsList = items.stream()
                    .map(item -> {
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("productId", item.getProductId());
                        itemMap.put("quantity", item.getQuantity());
                        itemMap.put("price", item.getPrice());
                        return itemMap;
                    })
                    .collect(Collectors.toList());

            // Создаем расширенный ответ с товарами
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", order.getId());
            response.put("cartId", order.getCartId());
            response.put("orderNumber", order.getOrderNumber());
            response.put("totalAmount", order.getTotalAmount());
            response.put("status", order.getStatus());
            response.put("clientId", order.getClientId());
            response.put("createdDate", order.getCreatedDate());
            response.put("items", itemsList);
            response.put("itemsCount", itemsList.size());

            log.info("✅ Заказ найден, товаров: {}", itemsList.size());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Заказ не найден: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Ошибка при получении заказа: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable int orderId,
                                               @RequestBody Map<String, Object> request) {
        try {
            String status = (String) request.get("status");
            if (status == null) {
                return badRequestResponse("Статус не указан");
            }

            log.info("🔄 PUT /orders/{}/status - status: {}", orderId, status);
            Order order = cartService.updateOrderStatus(orderId, status);
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException e) {
            return notFoundResponse(e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при обновлении статуса: {}", e.getMessage());
            return errorResponse("Ошибка при обновлении статуса", e);
        }
    }

    //Получить полную информацию о корзинах клиента
    @GetMapping("/client/{clientId}/full")
    public ResponseEntity<?> getClientCartsFull(@PathVariable int clientId) {
        try {
            log.info("🛍️ Получение полной информации о корзинах клиента {}", clientId);
            Map<String, Object> response = cartService.getClientCartsFull(clientId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Ошибка при получении информации: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Ошибка при получении данных", "message", e.getMessage()));
        }
    }

    //Получить заказы текущего пользователя
    @GetMapping("/client/{clientId}/my-orders")
    public ResponseEntity<?> getMyOrders(@PathVariable int clientId) {
        try {
            log.info("📦 Получение заказов для клиента {}", clientId);
            List<Map<String, Object>> orders = cartService.getMyOrders(clientId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "clientId", clientId,
                    "totalOrders", orders.size(),
                    "orders", orders
            ));
        } catch (Exception e) {
            log.error("❌ Ошибка при получении заказов: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    //Зевершить заказ
    @PostMapping("/{cartId}/complete-order")
    public ResponseEntity<?> completeOrder(@PathVariable int cartId) {
        try {
            log.info("✅ POST /cart/{}/complete-order - завершение заказа", cartId);
            Map<String, Object> response = cartService.completeOrder(cartId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Заказ не найден: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("⚠️ Некорректный статус заказа: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Ошибка при завершении заказа: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Ошибка при завершении заказа", "message", e.getMessage()));
        }
    }

    @GetMapping("/orders/all")
    public ResponseEntity<?> getAllOrders() {
        return ResponseEntity.ok(cartService.getAllOrders());
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> orderRequest) {
        log.info("📦 POST /api/cart/orders - получен запрос: {}", orderRequest);
        try {
            Map<String, Object> response = cartService.createOrder(orderRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Ошибка при создании заказа: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }


    // ==================== HEALTH ====================

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "service", "cart-service",
                    "timestamp", System.currentTimeMillis(),
                    "database", checkDatabase()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "status", "DOWN",
                            "error", e.getMessage()
                    ));
        }
    }

    private String checkDatabase() {
        try {
            cartRepository.count();
            return "connected";
        } catch (Exception e) {
            return "disconnected: " + e.getMessage();
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private ResponseEntity<Map<String, Object>> notFoundResponse(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "success", false,
                        "error", message
                ));
    }

    private ResponseEntity<Map<String, Object>> badRequestResponse(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
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
}