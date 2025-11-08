package com.warehouse.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.ElementCollection;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) // не отправлять null-поля в JSON
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

