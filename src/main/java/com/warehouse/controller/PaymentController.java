package com.warehouse.controller;

import com.stripe.exception.StripeException;
import com.warehouse.model.User;
import com.warehouse.service.PaymentService;
import com.warehouse.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    @GetMapping("/create-checkout-session")
    public String createCheckoutSession(@RequestParam Long userId) throws StripeException {
        int amount = 1500; // Например, $15
        return paymentService.createCheckoutSession(userId, amount);
    }

    @GetMapping("/success")
    public ResponseEntity<?> paymentSuccess(@RequestParam Long userId) {
        // Проверяем наличие пользователя
        User user = userService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.status(404).body("Пользователь не найден.");
        }

        // Статус подписки
        userService.updateUserPaymentStatus(userId, true);
        return ResponseEntity.ok("Подписка успешно активирована.");
    }


    @GetMapping("/cancel")
    public ResponseEntity<?> paymentCancel() {
        return ResponseEntity.ok("Оплата была отменена.");
    }

}
