package com.warehouse.repository;

import com.warehouse.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, String> {
    Optional<Item> findByName(String name); // Найти товар по названию

    @Query("SELECT i FROM Item i WHERE i.name = :name AND i.company.id = :companyId")
    Optional<Item> findByNameAndCompanyId(@Param("name") String name, @Param("companyId") Long companyId);

    @Query("SELECT i FROM Item i WHERE i.company.id = :companyId")
    List<Item> findAllByCompanyId(@Param("companyId") Long companyId);
}

