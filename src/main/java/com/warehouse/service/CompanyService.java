package com.warehouse.service;


import com.warehouse.model.Company;
import com.warehouse.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RequiredArgsConstructor
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;


    /**
     * Регистрация или поиск компании по названию.
     *
     * @param name название компании.
     * @return инстанс зарегистрированной или найденной компании.
     */
    public Company registerOrFindCompany(String name) {
        // Удаляем пробелы и приводим ввёденное название в нижний регистр
        String normalizedName = name.trim().toLowerCase();

        // Проверяем наличие компании в базе по имени
        return companyRepository.findByNameIgnoreCase(normalizedName)
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setName(normalizedName);
                    company.setIdentifier(generateIdentifier(normalizedName)); // Генерация уникального идентификатора
                    company.setEnabled(true); // Сразу активируем компанию
                    return companyRepository.save(company);
                });
    }

    /**
     * Генератор уникального идентификатора компании.
     *
     * @param name название компании.
     * @return уникальный идентификатор компании.
     */
    private String generateIdentifier(String name) {
        // Упрощённая генерация идентификатора (например, берётся часть названия + случайное число)
        return name.replace(" ", "_") + "_" + System.currentTimeMillis();
    }

    public Company startTrial(Company c, int days) {
        Instant now = Instant.now();
        c.setTrialStart(now);
        c.setTrialEnd(now.plus(days, ChronoUnit.DAYS));
        c.setSubscriptionActive(false);
        return companyRepository.save(c);
    }

    public boolean isCompanyAccessAllowed(Company c) {
        Instant now = Instant.now();
        if (c.isEnabled() == false) return false;

        // ACTIVE
        if (c.isSubscriptionActive() && c.getCurrentPeriodEnd() != null && now.isBefore(c.getCurrentPeriodEnd()))
            return true;

        // TRIAL
        if (c.getTrialStart() != null && c.getTrialEnd() != null && now.isBefore(c.getTrialEnd()))
            return true;

        return false; // иначе EXPIRED
    }

    /** Вспомогательно возвращать «дней осталось» по триалу или оплате */
    public long daysLeft(Company c) {
        Instant now = Instant.now();
        Instant end = null;
        if (c.isSubscriptionActive() && c.getCurrentPeriodEnd() != null) {
            end = c.getCurrentPeriodEnd();
        } else if (c.getTrialEnd() != null) {
            end = c.getTrialEnd();
        }
        if (end == null) return 0;
        long secs = end.getEpochSecond() - now.getEpochSecond();
        return Math.max(0, secs / 86400);
    }

}

