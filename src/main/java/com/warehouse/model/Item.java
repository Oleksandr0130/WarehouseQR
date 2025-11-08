package com.warehouse.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Entity
@Data
public class Item {
    @Id
    private String id;
    private String name;
    private int quantity;
    private int sold;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 14, scale = 2)
    private BigDecimal price;

    @Column(length = 8)
    private String currency;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "item_image", joinColumns = @JoinColumn(name = "item_id"))
    @Column(name = "data", columnDefinition = "TEXT")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<String> images = new ArrayList<>();

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "qr_code", columnDefinition = "bytea")
    private byte[] qrCode; // QR-код теперь хранится как массив байт

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false) // Связь с компанией
    private Company company;
}

