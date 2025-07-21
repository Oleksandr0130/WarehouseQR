package com.warehouse.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    @Value("${stripe.api.key}")
    private String apiKey;

    @Value("${app.base-url}")
    private String baseUrl;

    public String createCheckoutSession(Long userId, int amount) throws StripeException {
        Stripe.apiKey = apiKey;

        SessionCreateParams params = SessionCreateParams.builder()
                .setSuccessUrl(baseUrl + "/payment/success?userId=" + userId)
                .setCancelUrl(baseUrl + "/payment/cancel")
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount((long) amount * 100) // Сумма в центах
                                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                        .setName("Pro Account Subscription")
                                                        .build())
                                                .build())
                                .build())
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }
}
