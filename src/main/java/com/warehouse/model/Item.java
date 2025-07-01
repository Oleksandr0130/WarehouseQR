package com.warehouse.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Objects;

@Entity
@Data
public class Item {
    @Id
    private String id;
    private String name;
    private int quantity;
    private int sold;
    @Lob // Для хранения больших данных, добавляем аннотацию
    private byte[] qrCode; // QR-код теперь хранится как массив байт

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false) // Связь с компанией
    private Company company;
}

