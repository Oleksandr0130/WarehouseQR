package com.warehouse.service;

import com.warehouse.model.Company;
import com.warehouse.model.Payment;
import com.warehouse.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CompanyService companyService;
    private final PaymentProviderService paymentProviderService;

    /**
     * Инициация нового платежа.
     */
    public Payment initiatePayment(Company company, Double amount) {
        // Создание новой записи о платеже
        Payment payment = new Payment();
        payment.setCompany(company);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setPaymentStatus("INITIATED");
        payment.setAmount(amount);
        payment.setTransactionId(null); // Установим позже, после работы с провайдером

        return paymentRepository.save(payment);
    }

    /**
     * Завершение оплаты (успешной или неуспешной).
     */
    public Payment completePayment(String transactionId, boolean success) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Платеж не найден для транзакции: " + transactionId));

        payment.setPaymentStatus(success ? "SUCCESS" : "FAILED");
        paymentRepository.save(payment);

        if (success) {
            // Продляем подписку компании на 30 дней
            companyService.extendSubscription(payment.getCompany(), 30);
        }

        return payment;
    }
}

