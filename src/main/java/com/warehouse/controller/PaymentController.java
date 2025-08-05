package com.warehouse.controller;

import com.warehouse.model.Company;
import com.warehouse.model.Payment;
import com.warehouse.service.CompanyService;
import com.warehouse.service.PaymentService;
import com.warehouse.service.PaymentProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentProviderService paymentProviderService;
    private final CompanyService companyService;

    /**
     * Инициация платежа для продления подписки.
     */
    @PostMapping("/initiate/{companyId}")
    public ResponseEntity<String> initiatePayment(@PathVariable Long companyId) {
        Company company = companyService.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Компания не найдена"));

        // Указываем сумму подписки
        Double subscriptionAmount = 50.0; // $50 за месяц
        Payment payment = paymentService.initiatePayment(company, subscriptionAmount);

        // Генерация URL для оплаты
        String paymentUrl = paymentProviderService.createCheckoutSession(subscriptionAmount, "USD", company.getIdentifier());

        return ResponseEntity.ok(paymentUrl);
    }

    /**
     * Завершение платежа.
     */
    @PostMapping("/complete")
    public ResponseEntity<String> completePayment(@RequestParam String transactionId) {
        boolean success = paymentProviderService.verifyTransaction(transactionId);

        paymentService.completePayment(transactionId, success);

        return success ? ResponseEntity.ok("Платеж выполнен успешно") :
                ResponseEntity.badRequest().body("Ошибка платежа");
    }
}

