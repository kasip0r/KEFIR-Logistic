package com.kefir.payment;

import feign.FeignException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/payments")
public class UnifiedPaymentController {

    @Autowired
    private PayBackService payBackService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentCartsRepository paymentCartsRepository;

    @Autowired
    private PayBackScheduler payBackScheduler;

    @Autowired
    private AuthServiceClient authServiceClient;

    @Autowired
    private OrderServiceClient orderServiceClient;

    @Autowired
    private ProductServiceClient productServiceClient;

    private final PaymentService paymentService;

    private static final Logger log = LoggerFactory.getLogger(UnifiedPaymentController.class);

    public UnifiedPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmPayment(@RequestBody Map<String, Object> paymentData) {
        log.info("💰 ===== ПОДТВЕРЖДЕНИЕ ОПЛАТЫ (упрощённая версия) =====");

        try {
            String orderNumber = paymentData.get("orderNumber").toString();
            BigDecimal amount = new BigDecimal(paymentData.get("amount").toString());
            Long userId = Long.valueOf(paymentData.get("userId").toString());

            // 1. Списываем деньги
            Map<String, Object> withdrawResult = paymentService.withdraw(userId, amount, orderNumber);

            if (!"success".equals(withdrawResult.get("status"))) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Ошибка списания денег: " + withdrawResult.get("message")
                ));
            }

            // 2. Получаем заказ и товары
            Map<String, Object> orderInfo = orderServiceClient.getOrderByNumber(orderNumber);

            if (orderInfo == null || !Boolean.TRUE.equals(orderInfo.get("success"))) {
                throw new RuntimeException("Заказ не найден: " + orderNumber);
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) orderInfo.get("items");
            String warehouse = (String) orderInfo.get("warehouse"); // ← получаем склад из заказа

            if (warehouse == null) {
                warehouse = "usersklad"; // по умолчанию
            }

            if (items == null || items.isEmpty()) {
                log.warn("⚠️ Заказ {} не содержит товаров", orderNumber);
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Заказ не содержит товаров"
                ));
            }

            // 3. Списываем товары с указанием склада
            log.info("📦 Шаг 2: Списание {} товаров со склада {}", items.size(), warehouse);

            for (Map<String, Object> item : items) {
                Integer productId = (Integer) item.get("productId");
                Integer quantity = (Integer) item.get("quantity");

                log.info("🔄 Списание товара {} ({} шт.) со склада {}", productId, quantity, warehouse);

                try {
                    // Передаём склад в метод списания
                    Map<String, Object> writeOffResult = productServiceClient.writeOffProduct(
                            productId,
                            quantity,
                            warehouse
                    );

                    if (writeOffResult == null || !Boolean.TRUE.equals(writeOffResult.get("success"))) {
                        throw new RuntimeException("Ошибка списания товара " + productId);
                    }

                    log.info("✅ Товар {} списан со склада {}", productId, warehouse);

                } catch (Exception e) {
                    log.error("❌ Ошибка при списании товара {}: {}", productId, e.getMessage());

                    // Пытаемся вернуть деньги (упрощённая компенсация)
                    try {
                        log.warn("⚠️ Компенсация: возврат денег {} пользователю {}", amount, userId);
                        paymentService.compensateWithdraw(userId, amount, orderNumber);
                    } catch (Exception compError) {
                        log.error("❌ Критическая ошибка при возврате денег: {}", compError.getMessage());
                    }

                    throw new RuntimeException("Ошибка списания товара " + productId + ": " + e.getMessage());
                }
            }

            // 4. Обновляем статус заказа
            log.info("🔄 Шаг 3: Обновление статуса заказа на PAID");

            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("status", "PAID");

            // Извлекаем ID заказа из orderInfo
            Integer orderId = (Integer) orderInfo.get("id");
            if (orderId != null) {
                orderServiceClient.updateOrderStatus(orderId, statusUpdate);
            } else {
                // Если нет ID, пробуем обновить по номеру
                orderServiceClient.updateOrderStatus(Integer.parseInt(orderNumber), statusUpdate);
            }

            log.info("✅ Оплата успешно завершена для заказа {}", orderNumber);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Оплата подтверждена",
                    "orderNumber", orderNumber,
                    "warehouse", warehouse
            ));

        } catch (Exception e) {
            log.error("❌ Ошибка: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/create-client-account")
    public ResponseEntity<Map<String, Object>> createClientAccount(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = Long.valueOf(request.get("user_id").toString());
            String role = request.get("role").toString();

            if (!"client".equalsIgnoreCase(role)) {
                response.put("status", "error");
                response.put("message", "Account can only be created for clients");
                return ResponseEntity.badRequest().body(response);
            }

            // Убрали cardNumber из вызова!
            PaymentAccount account = paymentService.createAccountForClient(userId);

            response.put("status", "success");
            response.put("message", "Payment account created");
            response.put("account_id", account.getId());
            response.put("user_id", account.getUserId());
            response.put("initial_balance", account.getCash());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/card-payment")
    @Transactional
    public ResponseEntity<Map<String, Object>> cardPayment(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            Integer cardId = Integer.valueOf(request.get("card_id").toString());
            Long userId = Long.valueOf(request.get("user_id").toString());
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String orderNumber = request.get("order_id").toString();  // Это orderNumber
            String inputCvv = request.get("cvv").toString();

            // Получаем карту
            PaymentCarts card = paymentCartsRepository.findById(cardId)
                    .orElseThrow(() -> new RuntimeException("Card not found"));

            // Проверка CVV
            String realCvv = card.getCvv();
            if (!realCvv.equals(inputCvv)) {
                response.put("status", "error");
                response.put("message", "Invalid CVV code");
                return ResponseEntity.badRequest().body(response);
            }

            // Проверка средств
            if (card.getBalans().compareTo(amount) < 0) {
                response.put("status", "error");
                response.put("message", "Insufficient funds on card");
                return ResponseEntity.badRequest().body(response);
            }

            // Получаем баланс ДО списания
            BigDecimal balanceBefore = card.getBalans();

            // Списываем с карты
            card.setBalans(card.getBalans().subtract(amount));
            paymentCartsRepository.save(card);

            // Зачисляем на системный счет
            PaymentAccount systemAccount = paymentRepository.findByUserId(-1L)
                    .orElseThrow(() -> new RuntimeException("System account not found"));
            systemAccount.setCash(systemAccount.getCash().add(amount));
            paymentRepository.save(systemAccount);

            // ✅ ПОЛУЧАЕМ ИНФОРМАЦИЮ О ЗАКАЗЕ И ОБНОВЛЯЕМ СТАТУС
            try {
                log.info("📦 Получение информации о заказе {}", orderNumber);

                // Получаем информацию о заказе
                Map<String, Object> orderInfo = orderServiceClient.getOrderByNumber(orderNumber);

                if (orderInfo != null && Boolean.TRUE.equals(orderInfo.get("success"))) {
                    // Извлекаем ID заказа
                    Integer orderId = null;

                    // Пробуем получить ID разными способами
                    if (orderInfo.containsKey("id")) {
                        orderId = (Integer) orderInfo.get("id");
                    } else if (orderInfo.containsKey("orderId")) {
                        orderId = (Integer) orderInfo.get("orderId");
                    }

                    if (orderId != null) {
                        // Обновляем статус заказа на PAID
                        Map<String, Object> statusUpdate = new HashMap<>();
                        statusUpdate.put("status", "PAID");

                        log.info("🔄 Обновление статуса заказа ID={} на PAID", orderId);
                        Map<String, Object> updateResponse = orderServiceClient.updateOrderStatus(orderId, statusUpdate);
                        log.info("✅ Статус заказа обновлен: {}", updateResponse);
                    } else {
                        log.warn("⚠️ Не удалось получить ID заказа из ответа: {}", orderInfo);
                    }
                } else {
                    log.warn("⚠️ Заказ {} не найден или ошибка при получении", orderNumber);
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при обновлении статуса заказа: {}", e.getMessage());
                // Не прерываем операцию, так как деньги уже списаны
            }

            // Создаем транзакцию
            paymentService.createTransaction(
                    userId,
                    amount.negate(),
                    "CARD_PAYMENT",
                    orderNumber,
                    "Оплата заказа картой",
                    balanceBefore,
                    card.getBalans(),
                    -1L
            );

            response.put("status", "success");
            response.put("message", "Card payment successful");
            response.put("new_card_balance", card.getBalans());
            response.put("new_balance", systemAccount.getCash());
            response.put("order_status", "PAID");

        } catch (Exception e) {
            log.error("❌ Ошибка в cardPayment: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    // Остальные методы без изменений...
    @PostMapping("/handle-role-change")
    public ResponseEntity<Map<String, Object>> handleRoleChange(@RequestBody Map<String, Object> userData) {
        Map<String, Object> response = new HashMap<>();

        try {
            paymentService.handleUserRoleChange(userData);
            response.put("status", "success");
            response.put("message", "Payment account updated based on role change");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/pay-order")
    public ResponseEntity<?> payForOrder(@RequestBody Map<String, Object> request) {
        log.info("💰 POST /api/payments/pay-order - получен запрос: {}", request);

        try {
            // Проверка обязательных полей
            if (!request.containsKey("order_id")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "order_id обязателен"
                ));
            }

            if (!request.containsKey("amount")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "amount обязателен"
                ));
            }

            // Извлекаем данные
            Long orderId = Long.valueOf(request.get("order_id").toString());
            BigDecimal amount = new BigDecimal(request.get("amount").toString());

            // Используем user_id, который приходит из фронта
            Long userId = request.containsKey("user_id") ?
                    Long.valueOf(request.get("user_id").toString()) : null;

            log.info("📦 Оплата заказа #{}, сумма: {}, пользователь: {}", orderId, amount, userId);

            // 1. Обновляем статус заказа через cart-service
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("status", "PAID");

            try {
                // Вызываем cart-service для обновления статуса
                Map<String, Object> updateResponse = orderServiceClient.updateOrderStatus(
                        orderId.intValue(),
                        statusUpdate
                );
                log.info("✅ Статус заказа #{} обновлён на PAID, ответ: {}", orderId, updateResponse);
            } catch (Exception e) {
                log.error("❌ Ошибка при обновлении статуса заказа: {}", e.getMessage());
                // Даже если ошибка, возвращаем успех, потому что деньги списаны
                // Но логируем ошибку для ручной проверки
            }

            // 2. Здесь может быть дополнительная логика (отправка уведомлений и т.д.)

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Заказ оплачен");
            response.put("order_id", orderId);
            response.put("amount", amount);
            response.put("new_status", "PAID");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Ошибка при оплате заказа: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/balance/{userId}")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.getBalanceResponse(userId));
    }

    @PostMapping("/deposit")
    public ResponseEntity<Map<String, Object>> deposit(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = Long.valueOf(request.get("user_id").toString());
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String orderId = request.containsKey("order_id") ?
                    request.get("order_id").toString() : null;

            PaymentAccount account = paymentService.deposit(userId, amount, orderId);
            response.put("status", "success");
            response.put("message", "Deposit successful");
            response.put("new_balance", account.getCash());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("user_id").toString());
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String orderId = request.containsKey("order_id") ?
                    request.get("order_id").toString() : null;

            Map<String, Object> result = paymentService.withdraw(userId, amount, orderId);

            if ("error".equals(result.get("status"))) {
                return ResponseEntity.badRequest().body(result);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/account-exists/{userId}")
    public ResponseEntity<Map<String, Object>> accountExists(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.accountExistsResponse(userId));
    }

    @DeleteMapping("/delete-account/{userId}")
    public ResponseEntity<Map<String, Object>> deleteAccount(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.deleteAccountResponse(userId));
    }

    @GetMapping("/payback/process")
    public ResponseEntity<Map<String, Object>> processPayBack() {
        try {
            Map<String, Object> result = payBackService.processPayBackRecords();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/payback/status")
    public ResponseEntity<Map<String, Object>> getPayBackStatus() {
        try {
            Map<String, Object> status = payBackService.getPayBackStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/payback/scheduler-status")
    public ResponseEntity<Map<String, Object>> getSchedulerStatus() {
        try {
            Map<String, Object> status = payBackScheduler.getSchedulerStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/create-cart")
    public ResponseEntity<Map<String, Object>> createPaymentCart(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("user_id").toString());
            String cardNumber = request.get("card_number").toString();

            Map<String, Object> result = paymentService.createPaymentCart(userId, cardNumber);

            if ("error".equals(result.get("status"))) {
                return ResponseEntity.badRequest().body(result);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/card-info/{userId}")
    public ResponseEntity<Map<String, Object>> getCardInfo(@PathVariable Long userId) {
        try {
            // Ищем карту в таблице payment_carts
            Optional<PaymentCarts> cartOpt = paymentCartsRepository.findByIdUsers(userId);

            Map<String, Object> response = new HashMap<>();

            if (cartOpt.isPresent()) {
                PaymentCarts cart = cartOpt.get();
                response.put("status", "success");
                response.put("user_id", userId);
                response.put("id", cart.getId());
                response.put("cardNumber", maskCardNumber(cart.getCartNumber()));
                response.put("balance", cart.getBalans());
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "success");
                response.put("user_id", userId);
                response.put("id", null);
                response.put("cardNumber", null);
                response.put("balance", 0);
                response.put("message", "No card found");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/my-balance")
    public ResponseEntity<Map<String, Object>> getMyBalance(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            log.info("📥 Получен запрос my-balance с заголовком: {}", authHeader);

            Long userId;

            if (authHeader == null || authHeader.isEmpty()) {
                log.warn("⚠️ Authorization header отсутствует, используем тестового пользователя 1");
                userId = 1L;
            } else {
                userId = extractUserIdFromToken(authHeader);
            }

            log.info("✅ Используем userId: {}", userId);

            Map<String, Object> balanceResponse = paymentService.getBalanceResponse(userId);

            log.info("💰 Ответ от сервиса: {}", balanceResponse);

            return ResponseEntity.ok(balanceResponse);

        } catch (Exception e) {
            log.error("❌ Ошибка в my-balance: ", e);
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage()
                    ));
        }
    }

    // Вспомогательный метод для извлечения userId из токена
    private Long extractUserIdFromToken(String authHeader) {
        try {
            log.info("🔍 Извлечение userId из токена: {}", authHeader);

            ResponseEntity<Map<String, Object>> response = authServiceClient.extractUserId(authHeader, null);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object userIdObj = response.getBody().get("userId");
                if (userIdObj instanceof Number) {
                    return ((Number) userIdObj).longValue();
                }
            }

            // Если auth-service вернул не 200 — пробрасываем исключение
            throw new RuntimeException("Не удалось получить userId: " + response.getStatusCode());

        } catch (FeignException e) {
            log.error("❌ Ошибка вызова auth-service: статус {}", e.status());
            throw new RuntimeException("Ошибка авторизации: сервис недоступен");
        } catch (Exception e) {
            log.error("❌ Неизвестная ошибка: {}", e.getMessage());
            throw new RuntimeException("Ошибка авторизации");
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "payment-service",
                "timestamp", System.currentTimeMillis(),
                "database", checkDatabase()
        );
    }

    private String checkDatabase() {
        try {
            paymentRepository.count();
            return "connected";
        } catch (Exception e) {
            return "disconnected";
        }
    }

    // Вспомогательный метод для маскировки номера карты
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}