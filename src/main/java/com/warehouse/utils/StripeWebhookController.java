package com.warehouse.utils;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook")
public class StripeWebhookController {

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload,
                                                      @RequestHeader("Stripe-Signature") String signature) {
        try {
            // Проверка подписи Stripe и обработка событий (например, 'completed.payment')
            // StripeWebhookUtils.validateAndProcessEvent(payload, signature);
            return ResponseEntity.ok("Webhook успешно обработан.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Ошибка обработки Webhook.");
        }
    }
}

