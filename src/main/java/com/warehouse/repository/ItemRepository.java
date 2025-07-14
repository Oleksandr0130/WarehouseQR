package com.warehouse.repository;

import com.warehouse.model.Company;
import com.warehouse.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, String> {
    @Query("SELECT i FROM Item i WHERE i.name = :name AND i.company = :company")
    Optional<Item> findByNameAndCompany(@Param("name") String name, @Param("company") Company company);

    List<Item> findAllByCompany(Company company);
}


