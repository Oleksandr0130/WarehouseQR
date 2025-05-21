package com.warehouse.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
}