package com.warehouse.controller;

import com.warehouse.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    /**
     * Получение статуса подписки (остаток дней).
     *
     * @param companyId ID компании.
     * @return Количество оставшихся дней подписки или сообщение.
     */
    @GetMapping("/{companyId}/subscription-status")
    public ResponseEntity<Map<String, Long>> getSubscriptionStatus(@PathVariable Long companyId) {
        return companyService.findById(companyId)
                .map(company -> {
                    long daysLeft = 0;
                    if (company.getSubscriptionEndDate() != null) {
                        daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), company.getSubscriptionEndDate());
                        daysLeft = Math.max(0, daysLeft); // Убедимся, что подписка не возвращает отрицательное значение
                    }
                    return ResponseEntity.ok(Map.of("daysLeft", daysLeft));
                })
                .orElse(ResponseEntity.badRequest().body(Map.of("daysLeft", -1L)));
    }

}

