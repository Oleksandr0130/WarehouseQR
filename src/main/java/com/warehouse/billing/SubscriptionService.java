package com.warehouse.billing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

            // Разрешены только TRIAL/ACTIVE
            boolean isCandidate = "TRIAL".equals(body.status) || "ACTIVE".equals(body.status);
            if (!isCandidate) return false;

            // Если бек прислал daysLeft — считаем активным при > 0
            if (body.daysLeft != null) {
                return body.daysLeft > 0;
            }

            // Иначе — по дате окончания
            Instant now = Instant.now();
            if ("TRIAL".equals(body.status)) {
                Instant end = parseEndInstant(body.trialEnd);
                return end != null && end.isAfter(now);
            } else { // ACTIVE
                Instant end = parseEndInstant(body.currentPeriodEnd);
                return end != null && end.isAfter(now);
            }
        } catch (Exception ex) {
            // На ошибках (сетевых/парсинга) — считаем неактивным
            return false;
        }
    }

    /** Абсолютный URL до своего же /billing/status. */
    private String buildBillingStatusUrl(HttpServletRequest req) {
        String scheme = req.getScheme();                // http/https
        String host = req.getServerName();              // example.com/localhost
        int port = req.getServerPort();                 // 80/443/...
        String ctx = req.getContextPath();              // если есть
        String base = scheme + "://" + host + ((port == 80 || port == 443) ? "" : ":" + port)
                + (ctx != null ? ctx : "");
        return base + "/billing/status";
    }

    /**
     * Универсальный парсер даты окончания:
     * - пытается Instant (полный ISO с TZ),
     * - затем OffsetDateTime (с TZ-смещением),
     * - затем LocalDate ("YYYY-MM-DD"): в этом случае считаем активным до КОНЦА дня
     *   → возвращаем  (date + 1 day at 00:00Z).
     */
    private Instant parseEndInstant(String raw) {
        if (raw == null || raw.isBlank()) return null;

        // 1) Полный Instant
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {}

        // 2) OffsetDateTime → Instant
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ignored) {}

        // 3) Простой LocalDate "YYYY-MM-DD" → конец дня (exclusive)
        try {
            LocalDate d = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            // конец дня: следующий день в 00:00Z
            return d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {}

        return null;
    }

    /** DTO ответа /billing/status */
    public static class BillingStatusResponse {
        public String status;            // "TRIAL" | "ACTIVE" | "EXPIRED" | ...
        public String trialEnd;          // ISO-строка или "YYYY-MM-DD" или null
        public String currentPeriodEnd;  // ISO-строка или "YYYY-MM-DD" или null
        public Integer daysLeft;         // может быть null
        public Boolean isAdmin;          // не используется здесь
    }
}
