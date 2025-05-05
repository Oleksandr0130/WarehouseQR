package com.warehouse.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReservationDTO {
    private Long id;
    private String orderNumber;
    private String itemName;
    private int reservedQuantity;
    private String reservationWeek;
    private String status;
    private LocalDateTime saleDate;
    private String qrCode; // Новое поле для URL QR-кода

}

