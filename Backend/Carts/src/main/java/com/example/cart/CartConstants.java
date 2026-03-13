package com.example.cart;

public final class CartConstants {

    private CartConstants() {}

    // Статусы корзины
    public static final String CART_STATUS_ACTIVE = "active";
    public static final String CART_STATUS_PROCESSING = "processing";
    public static final String CART_STATUS_COMPLETED = "completed";
    public static final String CART_STATUS_CANCELLED = "cancelled";
    public static final String CART_STATUS_PROBLEM = "problem";

    // Статусы заказа
    public static final String ORDER_STATUS_CREATED = "CREATED";
    public static final String ORDER_STATUS_PAID = "PAID";
    public static final String ORDER_STATUS_PROCESSING = "PROCESSING";
    public static final String ORDER_STATUS_SHIPPED = "SHIPPED";
    public static final String ORDER_STATUS_DELIVERED = "DELIVERED";
    public static final String ORDER_STATUS_CANCELLED = "CANCELLED";

    // Статусы наличия
    public static final String NALICHIE_UNKNOWN = "unknown";
    public static final String NALICHIE_YES = "есть";
    public static final String NALICHIE_NO = "нет";
    public static final String NALICHIE_REFUNDED = "refunded";
}