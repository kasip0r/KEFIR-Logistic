package com.example.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductSklad, Integer> {

    // Поиск по артикулу
    Optional<ProductSklad> findByAkticul(String akticul);

    // Поиск по категории
    List<ProductSklad> findByCategory(String category);

    // Поиск по поставщику
    List<ProductSklad> findBySupplier(String supplier);

    // Поиск по имени (частичное совпадение, без учета регистра)
    List<ProductSklad> findByNameContainingIgnoreCase(String name);

    // Поиск товаров с низким запасом
    List<ProductSklad> findByCountLessThan(Integer count);

    // Проверка существования по артикулу
    boolean existsByAkticul(String akticul);
}