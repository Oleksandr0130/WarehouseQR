package com.warehouse.repository;

import com.warehouse.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByCompanyId(Long companyId); // Найти все платежи компании

    Optional<Payment> findByTransactionId(String transactionId); // Найти платеж по идентификатору транзакции

}

