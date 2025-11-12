package com.warehouse.model;

import jakarta.persistence.*;
import lombok.Data;

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

    /**
     * 👇 Эти методы не создают новых колонок в БД.
     * Они просто помогают фронту работать с булевым полем "admin".
     */
    @Transient
    public boolean isAdmin() {
        return "ROLE_ADMIN".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
    }

    @Transient
    public void setAdmin(boolean admin) {
        this.role = admin ? "ROLE_ADMIN" : "ROLE_USER";
    }
}
