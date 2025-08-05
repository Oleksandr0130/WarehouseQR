package com.warehouse.service;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentProviderService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.public-key}")
    private String stripePublicKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey; // Initialize Stripe SDK with the secret key
    }

    /**
     * Метод для создания Stripe Checkout Session.
     */
    public String createCheckoutSession(Double amount, String currency, String companyIdentifier) {
        try {
            // Параметры сессии
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl("https://your-domain.com/success?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl("https://your-domain.com/cancel")
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(currency)
                                    .setUnitAmount((long) (amount * 100)) // Stripe работает с минимальными единицами (например, центы)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Subscription for " + companyIdentifier)
                                            .build())
                                    .build())
                            .setQuantity(1L)
                            .build())
                    .build();

            // Создание сессии
            Session session = Session.create(params);

            return session.getUrl(); // Возвращаем URL для проведения сессии оплаты
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при создании Stripe Checkout Session: " + e.getMessage(), e);
        }
    }

    /**
     * Проверка статуса транзакции на основании Stripe sessionId.
     */
    public boolean verifyTransaction(String sessionId) {
        try {
            Session session = Session.retrieve(sessionId);

            return "complete".equalsIgnoreCase(session.getStatus());
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при проверке Stripe Transaction: " + e.getMessage(), e);
        }
    }
}

