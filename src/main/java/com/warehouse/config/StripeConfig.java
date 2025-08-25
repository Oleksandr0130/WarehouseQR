package com.warehouse.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${app.stripe.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;

        // --- ВАЖНО: сетевые таймауты и ретраи, чтобы не ловить 504 от DO ---
        // таймаут на установление TCP-соединения
        Stripe.setConnectTimeout(10_000);  // 10s
        // таймаут на чтение ответа
        Stripe.setReadTimeout(20_000);     // 20s
        // ограничим количество сетевых повторов (по умолчанию 2)
        Stripe.setMaxNetworkRetries(1);

        // (необязательно) отключить телеметрию SDK
        // Stripe.setTelemetryEnabled(false);
    }
}
