package com.warehouse.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    @Value("${app.stripe.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        // Устанавливаем ключ и таймауты 1 раз на всё приложение
        Stripe.apiKey = apiKey;
        Stripe.setConnectTimeout(10_000); // 10s
        Stripe.setReadTimeout(10_000);    // 10s
        Stripe.setMaxNetworkRetries(2);
        log.info("Stripe configured: connectTimeout=10s, readTimeout=10s, retries=2");
    }
}
