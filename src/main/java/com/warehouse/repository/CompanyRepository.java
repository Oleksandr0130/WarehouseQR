package com.warehouse.repository;

import com.warehouse.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    // Метод для поиска компании по имени (нечувствителен к регистру)
    Optional<Company> findByNameIgnoreCase(String name);
    Optional<Company> findByPaymentCustomerId(String paymentCustomerId);

}


