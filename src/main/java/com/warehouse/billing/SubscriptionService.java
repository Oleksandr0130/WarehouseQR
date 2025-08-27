package com.warehouse.billing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Проверка активности доступов (trial/subscription) через вызов твоего же /billing/status
 * с тем же Authorization, что пришёл в запросе.
 *
 * ВАЖНО: при server.servlet.context-path=/api конечный URL будет /api/billing/status,
 * поэтому здесь НЕ нужно добавлять /api вручную.
 */
@Service
public class SubscriptionService {

    private final RestTemplate restTemplate = new RestTemplate();

    /** true, если статус TRIAL/ACTIVE и срок не истёк */
    public boolean hasActiveAccess(HttpServletRequest request) {
        try {
            URI url = buildBillingStatusUrl(request);

            HttpHeaders headers = new HttpHeaders();
            String auth = request.getHeader("Authorization");
            if (auth != null && !auth.isBlank()) {
                headers.set("Authorization", auth);
            }

            // Пробрасывать куки обычно не нужно для API, но не помешает:
            String cookie = request.getHeader("Cookie");
            if (cookie != null && !cookie.isBlank()) {
                headers.set("Cookie", cookie);
            }

            ResponseEntity<BillingStatusResponse> resp =
                    restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), BillingStatusResponse.class);

            BillingStatusResponse b = resp.getBody();
            if (b == null || b.status == null) return false;

            boolean candidate = "TRIAL".equals(b.status) || "ACTIVE".equals(b.status);
            if (!candidate) return false;

            if (b.daysLeft != null) return b.daysLeft > 0;

            Instant now = Instant.now();
            if ("TRIAL".equals(b.status) && b.trialEnd != null) {
                Instant end = parseIso(b.trialEnd);
                return end != null && end.isAfter(now);
            }
            if ("ACTIVE".equals(b.status) && b.currentPeriodEnd != null) {
                Instant end = parseIso(b.currentPeriodEnd);
                return end != null && end.isAfter(now);
            }
            return false;
        } catch (Exception ex) {
            // можно залогировать при желании
            return false;
        }
    }

    /**
     * Собираем абсолютный URL к /billing/status с учётом:
     * - server.servlet.context-path (например, /api)
     * - обратного прокси (X-Forwarded-Proto/Host/Port)
     */
    private URI buildBillingStatusUrl(HttpServletRequest req) {
        // 1) читаем forwarded-заголовки, если стоишь за прокси/Ingress
        String forwardedProto = headerOrNull(req, "X-Forwarded-Proto");
        String forwardedHost  = headerOrNull(req, "X-Forwarded-Host");
        String forwardedPort  = headerOrNull(req, "X-Forwarded-Port");

        String scheme = (forwardedProto != null) ? forwardedProto : req.getScheme();
        String host   = (forwardedHost  != null) ? forwardedHost  : req.getServerName();
        int    port   = (forwardedPort  != null) ? safeParseInt(forwardedPort, req.getServerPort()) : req.getServerPort();

        String ctx = req.getContextPath(); // например, "/api" или ""

        // НЕ добавляем "/api" вручную — contextPath уже его включает
        String path = (ctx != null ? ctx : "") + "/billing/status";

        // 2) формируем URI. Для 80/443 порт можно опустить.
        boolean omitPort = (("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443));

        String base = scheme + "://" + host + (omitPort ? "" : ":" + port);
        return URI.create(base + path);
    }

    private String headerOrNull(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return (v != null && !v.isBlank()) ? v : null;
    }

    private int safeParseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw.trim()); } catch (Exception e) { return fallback; }
    }

    private Instant parseIso(String raw) {
        try { return Instant.parse(raw); } catch (DateTimeParseException e) { return null; }
    }

    /** DTO ответа твоего /billing/status */
    public static class BillingStatusResponse {
        public String status;            // TRIAL | ACTIVE | EXPIRED | ...
        public String trialEnd;
        public String currentPeriodEnd;
        public Integer daysLeft;
        public Boolean isAdmin;
    }
}
