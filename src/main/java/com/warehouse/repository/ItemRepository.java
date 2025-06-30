package com.warehouse.repository;

import com.warehouse.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, String> {
    Optional<Item> findByName(String name); // Найти товар по названию

}
