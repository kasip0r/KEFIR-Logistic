package com.example.product;

import java.time.LocalDateTime;

public interface BaseProduct {
    Integer getId();
    String getName();
    Double getPrice();
    Integer getCount();
    String getAkticul();
    String getCategory();
    String getDescription();
    String getSupplier();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();

    void setName(String name);
    void setPrice(Double price);
    void setCount(Integer count);
    void setAkticul(String akticul);
    void setCategory(String category);
    void setDescription(String description);
    void setSupplier(String supplier);
    void setUpdatedAt(LocalDateTime updatedAt);
}