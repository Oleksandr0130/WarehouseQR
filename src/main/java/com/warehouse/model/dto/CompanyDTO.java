package com.warehouse.model.dto;

import com.warehouse.model.Company;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CompanyDTO {
    private Long id;
    private String name;
    private boolean enabled;
    private LocalDate subscriptionEndDate;

    public CompanyDTO(Company company) {
        this.id = company.getId();
        this.name = company.getName();
        this.enabled = company.isEnabled();
        this.subscriptionEndDate = company.getSubscriptionEndDate();
    }
}

