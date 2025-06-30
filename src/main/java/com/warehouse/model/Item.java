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

    @ManyToOne // Связь с таблицей Company
    @JoinColumn(name = "company_id") // Указание FK
    private Company company; // Поле компании, связанной с товаром
}


