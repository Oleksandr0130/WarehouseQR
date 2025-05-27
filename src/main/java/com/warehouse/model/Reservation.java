package com.warehouse.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Data
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderNumber; // Номер заказа в формате "2515303-01-01"
    private String itemName;    // Название зарезервированного товара
    private int reservedQuantity; // Количество зарезервированного товара
    private String reservationWeek; // Например, "KW22"
    private String status;     // Статус резервации ("RESERVED", "SOLD")
    private LocalDateTime saleDate;
    @Lob // Для хранения длинных строк (Base64 содержимого QR-кода)
    private String qrCode; // QR-код резервации в формате Base64


}