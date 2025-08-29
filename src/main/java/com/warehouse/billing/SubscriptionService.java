package com.warehouse.billing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Проверка активности доступа (trial/active) через вызов твоего же /billing/status
 * с тем же Authorization, что пришёл в запросе.
 * Работает одинаково при context-path "" и "/api".
 */
@Service
public class SubscriptionService {

    private final RestTemplate restTemplate = new RestTemplate();

    /** true, если статус TRIAL/ACTIVE и срок не истёк */
    public boolean hasActiveAccess(HttpServletRequest request) {
        try {
            String url = buildBillingStatusUrl(request);
            HttpHeaders headers = new HttpHeaders();

            String auth = request.getHeader("Authorization");
            if (auth != null && !auth.isBlank()) {
                headers.set("Authorization", auth);
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
            return false;
        }
    }

    private String buildBillingStatusUrl(HttpServletRequest req) {
        String scheme = req.getScheme();
        String host = req.getServerName();
        int port = req.getServerPort();
        String ctx = req.getContextPath(); // "" или "/api"
        String base = scheme + "://" + host + ((port == 80 || port == 443) ? "" : ":" + port)
                + (ctx != null ? ctx : "");
        // ВАЖНО: тут НЕ добавляем ещё раз /api — context-path уже учтен в ctx
        return base + "/billing/status";
    }

    private Instant parseIso(String raw) {
        try { return Instant.parse(raw); } catch (DateTimeParseException e) { return null; }
    }

    /** DTO ответа /billing/status */
    public static class BillingStatusResponse {
        public String status;            // TRIAL | ACTIVE | EXPIRED | ...
        public String trialEnd;
        public String currentPeriodEnd;
        public Integer daysLeft;
        public Boolean isAdmin;
    }
}
