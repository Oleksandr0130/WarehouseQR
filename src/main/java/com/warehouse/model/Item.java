package com.warehouse.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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

    @Lob
    private byte[] qrCode; // Ссылка на QR-код товара

}
