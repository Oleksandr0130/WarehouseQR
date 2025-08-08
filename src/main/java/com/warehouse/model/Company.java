package com.warehouse.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "companies")
@Data
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Стратегия генерации идентификаторов
    private Long id;

    @Column(unique = true, nullable = false) // Уникальное название компании
    private String name;

    @Column(unique = true, nullable = false) // Уникальный идентификатор компании (может быть опциональным)
    private String identifier;

    private boolean enabled = false; // Активность компании

    // новая логика подписки
    private Instant trialStart;           // начало пробного периода
    private Instant trialEnd;             // конец пробного периода
    private boolean subscriptionActive;   // флаг активной подписки
    private String stripeCustomerId;      // ID клиента в Stripe
    private String stripeSubscriptionId;  // ID подписки в Stripe
    private Instant currentPeriodEnd; // конец текущего оплаченного периода (если оплачено)

    // удобно иметь геттер статуса
    @Transient
    public String getSubscriptionStatus() {
        Instant now = Instant.now();
        if (subscriptionActive && currentPeriodEnd != null && now.isBefore(currentPeriodEnd)) {
            return "ACTIVE";
        }
        if (trialStart != null && trialEnd != null) {
            if (now.isBefore(trialEnd)) return "TRIAL";
        }
        // можно потом расширить PAST_DUE/GRACE
        return "EXPIRED";
    }
}

