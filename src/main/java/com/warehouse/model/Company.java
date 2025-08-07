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
}

