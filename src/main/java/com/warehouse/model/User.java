package com.warehouse.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;


@Entity
@Table(name = "users", uniqueConstraints = { @UniqueConstraint(columnNames = "email") })
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String password;

    private String email;

    private String role; // например, ROLE_USER

    private boolean enabled = false; // активен после подтверждения email

    private String confirmationCode;

    private Instant confirmationExpiry;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company; // Указание на компанию, к которой привязан пользователь
}


