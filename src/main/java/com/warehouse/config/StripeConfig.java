package com.warehouse.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    // ДЕЛАЕМ КЛЮЧ НЕОБЯЗАТЕЛЬНЫМ: если не задан, не валим приложение
    @Value("${app.stripe.api-key:}")
    private String apiKey;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[StripeConfig] WARNING: app.stripe.api-key is NOT set. Stripe is disabled.");
            return;
        }

        Stripe.apiKey = apiKey;

        // --- ВАЖНО: сетевые таймауты и ретраи, чтобы не ловить 504 от DO ---
        Stripe.setConnectTimeout(10_000);  // 10s на установление TCP-соединения
        Stripe.setReadTimeout(20_000);     // 20s на чтение ответа
        Stripe.setMaxNetworkRetries(1);    // не растягивать запрос ретраями

        // (опционально) отключить телеметрию SDK
        // Stripe.setTelemetryEnabled(false);

        System.out.println("[StripeConfig] Stripe initialized with safe timeouts");
    }
}
