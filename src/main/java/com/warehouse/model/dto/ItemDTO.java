package com.warehouse.model.dto;

import jakarta.persistence.ElementCollection;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Data
public class ItemDTO {
    private String id;
    private String name;
    private int quantity;
    private int sold;
    private String qrCode; // Новое поле для URL QR-кода
    private String description;
    private BigDecimal price;
    private String currency;
    private List<String> images;

}

