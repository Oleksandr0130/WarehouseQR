package com.warehouse.controller;

import lombok.Data;

@Data
public class ReservationRequest {
    private String orderNumber;
    private String itemName;
    private int quantity;
    private String reservationWeek;
}
