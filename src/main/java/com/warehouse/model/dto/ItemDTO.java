package com.warehouse.model.dto;

import lombok.Data;

import java.util.Objects;

@Data
public class ItemDTO {
    private String id;
    private String name;
    private int quantity;
    private int sold;
    private String qrCodeBase64; // QR-код в формате Base64

}

