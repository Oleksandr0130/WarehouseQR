package com.warehouse.config;

import com.warehouse.model.Company;
import com.warehouse.repository.CompanyRepository;
import com.warehouse.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailReminderService {

    private final CompanyRepository companyRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 9 * * ?") // Каждый день в 9 утра
    public void sendTrialExpiryReminders() {
        LocalDate twoDaysFromNow = LocalDate.now().plusDays(2);
        List<Company> expiringCompanies = companyRepository.findAll()
                .stream()
                .filter(company -> company.getSubscriptionEndDate() != null &&
                        company.getSubscriptionEndDate().equals(twoDaysFromNow))
                .toList();

        expiringCompanies.forEach(company -> {
            String subject = "Ваша подписка истекает через 2 дня!";
            String message = String.format("Уважаемые пользователи компании '%s',\n\n" +
                            "Ваша подписка истекает %s. Пожалуйста, обновите её, чтобы продолжить пользоваться нашими сервисами.",
                    company.getName(), company.getSubscriptionEndDate());
            emailService.sendGenericEmail(company.getIdentifier(), subject, message); // Допустим метод отправки email
        });
    }
}

