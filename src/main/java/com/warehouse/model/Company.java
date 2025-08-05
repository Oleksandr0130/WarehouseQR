package com.warehouse.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    private LocalDate registrationDate;// Дата регистрации компании

    private LocalDate subscriptionEndDate;// Дата окончания пробного триала или подписки
}

