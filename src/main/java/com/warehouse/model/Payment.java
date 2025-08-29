package com.warehouse.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_company_id", columnList = "company_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false, length = 16)
    private String provider;   // "stripe"

    @Column(nullable = false, length = 32)
    private String method;     // "card", "blik", ...

    @Column(nullable = false, length = 8)
    private String currency;   // "PLN", "EUR"

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 16)
    private String status;     // "paid"

    @Column(nullable = false, length = 128)
    private String transactionId; // PaymentIntent ID

    @Column(nullable = false)
    private Instant paidAt;

    @Column(nullable = false)
    private Instant periodStart;

    @Column(nullable = false)
    private Instant periodEnd;

    @Lob
    private String rawPayload; // JSON Stripe Session для аудита
}
