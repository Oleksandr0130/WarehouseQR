package com.warehouse.service;

import com.warehouse.model.Company;
import com.warehouse.repository.CompanyRepository;
import com.warehouse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RequiredArgsConstructor
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository; // добавили, чтобы по пользователю найти компанию

    /**
     * Регистрация или поиск компании по названию.
     */
    public Company registerOrFindCompany(String name) {
        String normalizedName = name.trim().toLowerCase();
        return companyRepository.findByNameIgnoreCase(normalizedName)
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setName(normalizedName);
                    company.setIdentifier(generateIdentifier(normalizedName)); // Генерация уникального идентификатора
                    company.setEnabled(true);
                    return companyRepository.save(company);
                });
    }

    private String generateIdentifier(String name) {
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
        if (!c.isEnabled()) return false;

        // ACTIVE
        if (c.isSubscriptionActive() && c.getCurrentPeriodEnd() != null && now.isBefore(c.getCurrentPeriodEnd()))
            return true;

        // TRIAL
        if (c.getTrialStart() != null && c.getTrialEnd() != null && now.isBefore(c.getTrialEnd()))
            return true;

        return false;
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

    // ========= NEW: активация подписки из Google Play Billing =========

    /**
     * Активирует подписку для компании пользователя после успешной проверки purchaseToken у Google.
     * @param username логин пользователя (Principal.getName())
     * @param productId ID подписки в Play Console (например "flowqr_standard")
     * @param expiryMillis время окончания периода в миллисекундах с эпохи
     */
    public void activateFromGoogle(String username, String productId, long expiryMillis) {
        // Находим пользователя и его компанию (ожидается, что у User есть getCompany())
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        Company c = user.getCompany();
        if (c == null) {
            throw new IllegalStateException("Company not linked to user: " + username);
        }

        // Включаем подписку и сбрасываем триал
        c.setSubscriptionActive(true);
        c.setCurrentPeriodEnd(Instant.ofEpochMilli(expiryMillis));
        c.setTrialStart(null);
        c.setTrialEnd(null);

        // (опционально) можно сохранить productId/source в полях Company, если они есть
        // c.setSubscriptionProduct(productId);
        // c.setSubscriptionSource("GOOGLE");

        companyRepository.save(c);
    }
}
