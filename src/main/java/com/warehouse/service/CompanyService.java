package com.warehouse.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
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
    private final StripeService stripeService;

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
                    // Создание компании, если её нет
                    Instant now = Instant.now();
                    Company company = new Company();
                    company.setName(normalizedName);
                    company.setIdentifier(generateIdentifier(normalizedName)); // Генерация уникального идентификатора
                    company.setEnabled(true); // Сразу активируем компанию
                    // пробный период 5 дней
                    company.setTrialStart(now);
                    company.setTrialEnd(now.plus(5, ChronoUnit.DAYS));
                    // создаём клиента Stripe
                    Customer cust = null;
                    try {
                        cust = stripeService.createCustomer(/*email*/"", company.getName());
                    } catch (StripeException e) {
                        throw new RuntimeException(e);
                    }
                    company.setStripeCustomerId(cust.getId());
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
}

