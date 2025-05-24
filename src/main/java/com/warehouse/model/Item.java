package com.warehouse.model;

import jakarta.persistence.Column;
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
    private String qrCode; // Ссылка на QR-код товара

    @Lob
    @Column(columnDefinition = "BLOB") // Хранение изображения как BLOB
    private byte[] qrCodeImage; // Поле для хранения массива байтов изображения QR-кода


}
