package com.example.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByCartId(int cartId);
    Order findByOrderNumber(String orderNumber);
    List<Order> findByStatus(String status);
    List<Order> findByClientId(int clientId);

    List<Order> findByStatusAndCreatedDateBefore(String status, LocalDateTime dateTime);

}