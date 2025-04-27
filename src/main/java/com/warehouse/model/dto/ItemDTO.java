package com.warehouse.model.dto;

import lombok.Data;

@Data
public class ItemDTO {
    private String id;
    private String name;
    private int quantity;
    private int sold;
}

