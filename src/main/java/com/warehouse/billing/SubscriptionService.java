package com.warehouse.billing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Проверка активности доступа (trial/subscription) для текущего пользователя.
 * Реализована через вызов твоего /billing/status с тем же JWT.
 */
@Service
public class SubscriptionService {

    private final RestTemplate restTemplate = new RestTemplate();

    /** Возвращает true, если у пользователя TRIAL или ACTIVE и срок не истёк. */
    public boolean hasActiveAccess(HttpServletRequest request) {
        try {
            String url = buildBillingStatusUrl(request);
            HttpHeaders headers = new HttpHeaders();

            String auth = request.getHeader("Authorization");
            if (auth != null && !auth.isBlank()) {
                headers.set("Authorization", auth);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<BillingStatusResponse> resp =
                    restTemplate.exchange(url, HttpMethod.GET, entity, BillingStatusResponse.class);

            BillingStatusResponse body = resp.getBody();
            if (body == null || body.status == null) return false;

            // активные состояния
            if (!("TRIAL".equals(body.status) || "ACTIVE".equals(body.status))) {
                return false;
            }

            // если пришли daysLeft — используем его
            if (body.daysLeft != null) {
                return body.daysLeft > 0;
            }

            // иначе — проверим по датам окончания
            Instant now = Instant.now();
            if ("TRIAL".equals(body.status) && body.trialEnd != null) {
                Instant end = parseIsoInstant(body.trialEnd);
                return end != null && end.isAfter(now);
            }
            if ("ACTIVE".equals(body.status) && body.currentPeriodEnd != null) {
                Instant end = parseIsoInstant(body.currentPeriodEnd);
                return end != null && end.isAfter(now);
            }

            // если ничего не пришло — считаем неактивным
            return false;
        } catch (Exception ex) {
            // на ошибке считаем доступ неактивным (чтобы не пускать)
            return false;
        }
    }

    /** Собираем абсолютный URL до своего же /billing/status на основе текущего запроса. */
    private String buildBillingStatusUrl(HttpServletRequest req) {
        String scheme = req.getScheme();                // http/https
        String host = req.getServerName();              // example.com / localhost
        int port = req.getServerPort();                 // 80/443/...
        String ctx = req.getContextPath();              // если есть
        String base = scheme + "://" + host + ((port == 80 || port == 443) ? "" : ":" + port)
                + (ctx != null ? ctx : "");
        return base + "/billing/status";
    }

    private Instant parseIsoInstant(String iso) {
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** DTO ответа твоего /billing/status */
    public static class BillingStatusResponse {
        public String status;            // "TRIAL" | "ACTIVE" | "EXPIRED" | ...
        public String trialEnd;          // ISO-строка или null
        public String currentPeriodEnd;  // ISO-строка или null
        public Integer daysLeft;         // может быть null
        public Boolean isAdmin;          // не используется здесь
    }
}
