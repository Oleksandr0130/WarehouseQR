package com.warehouse.model;

import jakarta.persistence.*;
import lombok.Data;


@Entity
@Data
public class Item {
    @Id
    private String id;
    private String name;
    private int quantity;
    private int sold;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "qr_code", columnDefinition = "bytea")
    private byte[] qrCode; // QR-код теперь хранится как массив байт

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false) // Связь с компанией
    private Company company;
}

