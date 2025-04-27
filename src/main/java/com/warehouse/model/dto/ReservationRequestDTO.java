package com.warehouse.model.dto;

import lombok.Data;

@Data
public class ReservationRequestDTO {
    private String orderNumber;
    private String itemName;
    private int quantity;
    private String reservationWeek;
}

