package com.example.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationScheduler.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductServiceClient productServiceClient;

    //Установка времени проверки зарезервированных корзин
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void releaseExpiredReservations() {
        log.info("⏰ Проверка просроченных резервов...");

        //Установка времени резервации в минутах
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(1);

        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedDateBefore("PENDING", threshold);

        if (expiredOrders.isEmpty()) {
            log.info("✅ Просроченных резервов нет");
            return;
        }

        log.info("📦 Найдено {} просроченных заказов", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {
                log.info("🔄 Обработка заказа ID: {}, номер: {}", order.getId(), order.getOrderNumber());

                // Получаем товары из корзины
                List<CartItem> items = cartItemRepository.findByCartId(order.getCartId());

                if (items.isEmpty()) {
                    log.info("⚠️ В корзине {} нет товаров", order.getCartId());
                } else {
                    log.info("📦 Возвращаем {} товаров на склад", items.size());

                    // ТОЧНО КАК В removeFromCart!
                    for (CartItem item : items) {
                        try {
                            log.info("🔄 Возврат товара {} ({} шт.)",
                                    item.getProductId(), item.getQuantity());

                            Map<String, Object> releaseResponse = productServiceClient.releaseProduct(
                                    item.getProductId(),
                                    item.getQuantity(),
                                    order.getWarehouse()
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

                // Обновляем статус заказа
                order.setStatus("EXPIRED");
                orderRepository.save(order);
                log.info("✅ Статус заказа {} изменен на EXPIRED", order.getId());

            } catch (Exception e) {
                log.error("❌ Критическая ошибка при обработке заказа {}: {}",
                        order.getId(), e.getMessage(), e);
            }
        }

        log.info("✅ Обработка просроченных резервов завершена. Обработано: {}", expiredOrders.size());
    }
}