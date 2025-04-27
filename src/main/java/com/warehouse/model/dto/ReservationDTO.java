package com.warehouse.model.dto;

import lombok.Data;

@Data
public class ReservationDTO {
    private Long id;
    private String orderNumber;
    private String itemName;
    private int reservedQuantity;
    private String reservationWeek;
    private String status;
}

