package com.warehouse.repository;

import com.warehouse.model.Company;
import com.warehouse.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, String> {
    Optional<Item> findByName(String name, Company company); // Найти товар по названию и компании

    List<Item> findAllByCompany(Company company); // Найти все товары для определенной компании
}

