package com.warehouse.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company; // Компания, для которой осуществляется платеж

    private LocalDateTime paymentDate; // Дата платежа

    private String paymentStatus; // Статус: INITIATED, SUCCESS, FAILED

    private Double amount; // Сумма платежа

    private String transactionId; // Идентификатор транзакции от провайдера платежей

}
