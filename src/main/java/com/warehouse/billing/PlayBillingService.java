package com.warehouse.billing;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;

@Service
public class PlayBillingService {

    private volatile AndroidPublisher cachedPublisher; // ➕ ADDED

    private AndroidPublisher androidPublisher() throws Exception {
        // ➕ ADDED: кешируем клиент
        if (cachedPublisher != null) return cachedPublisher;

        synchronized (this) {
            if (cachedPublisher != null) return cachedPublisher;

            GoogleCredentials creds;

            String json = System.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON");
            if (json != null && !json.isBlank()) {
                try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
                    creds = GoogleCredentials.fromStream(in)
                            .createScoped(Collections.singletonList("https://www.googleapis.com/auth/androidpublisher"));
                }
            } else {
                // fallback: ADC
                creds = GoogleCredentials.getApplicationDefault()
                        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/androidpublisher"));
            }

            var reqInit = new HttpCredentialsAdapter(creds);

            cachedPublisher = new AndroidPublisher.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                    reqInit
            ).setApplicationName("FlowQR").build();

            return cachedPublisher;
        }
    }

    public SubscriptionPurchase verify(String packageName, String productId, String purchaseToken) throws Exception {
        return androidPublisher()
                .purchases()
                .subscriptions()
                .get(packageName, productId, purchaseToken)
                .execute();
    }

    public boolean isActive(SubscriptionPurchase sp) {
        try {
            Long expiryMs = sp.getExpiryTimeMillis();
            return expiryMs != null && expiryMs > Instant.now().toEpochMilli();
        } catch (Exception ignored) {
            return false;
        }
    }

    public long expiryMillis(SubscriptionPurchase sp) {
        Long expiryMs = sp.getExpiryTimeMillis();
        return expiryMs != null ? expiryMs : 0L;
    }
}
