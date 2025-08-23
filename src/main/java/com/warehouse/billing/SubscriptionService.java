package com.warehouse.billing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Проверка активности (trial/subscription) через вызов /billing/status с тем же JWT. */
@Service
public class SubscriptionService {

    private final RestTemplate restTemplate = new RestTemplate();

    /** true, если TRIAL или ACTIVE и срок не истёк. */
    public boolean hasActiveAccess(HttpServletRequest request) {
        try {
            String url = buildBillingStatusUrl(request);
            HttpHeaders headers = new HttpHeaders();
            String auth = request.getHeader("Authorization");
            if (auth != null && !auth.isBlank()) headers.set("Authorization", auth);

            ResponseEntity<BillingStatusResponse> resp =
                    restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), BillingStatusResponse.class);

            BillingStatusResponse body = resp.getBody();
            if (body == null || body.status == null) return false;

            boolean candidate = "TRIAL".equals(body.status) || "ACTIVE".equals(body.status);
            if (!candidate) return false;

            if (body.daysLeft != null) return body.daysLeft > 0;

            Instant now = Instant.now();
            if ("TRIAL".equals(body.status)) {
                Instant end = parseEndInstant(body.trialEnd);
                return end != null && end.isAfter(now);
            } else {
                Instant end = parseEndInstant(body.currentPeriodEnd);
                return end != null && end.isAfter(now);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String buildBillingStatusUrl(HttpServletRequest req) {
        String scheme = req.getScheme();
        String host = req.getServerName();
        int port = req.getServerPort();
        String ctx = req.getContextPath();
        String base = scheme + "://" + host + ((port == 80 || port == 443) ? "" : ":" + port) + (ctx != null ? ctx : "");
        return base + "/api/billing/status";
    }

    /** Понимает Instant/OffsetDateTime/LocalDate; для LocalDate — до конца дня (exclusive). */
    private Instant parseEndInstant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Instant.parse(raw); } catch (DateTimeParseException ignored) {}
        try { return OffsetDateTime.parse(raw).toInstant(); } catch (DateTimeParseException ignored) {}
        try {
            LocalDate d = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            return d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {}
        return null;
    }

    /** DTO ответа /billing/status */
    public static class BillingStatusResponse {
        public String status;            // TRIAL | ACTIVE | EXPIRED | ...
        public String trialEnd;          // ISO или YYYY-MM-DD
        public String currentPeriodEnd;  // ISO или YYYY-MM-DD
        public Integer daysLeft;         // опционально
        public Boolean isAdmin;          // не используется
    }
}
