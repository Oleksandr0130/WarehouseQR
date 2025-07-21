package com.warehouse.controller;

import com.stripe.exception.StripeException;
import com.warehouse.service.PaymentService;
import com.warehouse.service.UserService;
import lombok.RequiredArgsConstructor;
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
    public String paymentSuccess(@RequestParam Long userId) {
        userService.updateUserPaymentStatus(userId, true);
        return "Payment was successful!";
    }

    @GetMapping("/cancel")
    public String paymentCancel() {
        return "Payment was canceled!";
    }
}
