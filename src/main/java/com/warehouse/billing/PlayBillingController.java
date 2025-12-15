package com.warehouse.billing;

import com.warehouse.service.CompanyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * Принимает purchaseToken от APK, проверяет у Google и активирует доступ.
 *
 * ✅ ВАЖНО:
 *  server.servlet.context-path=/api уже добавит /api автоматически.
 *  Поэтому тут НЕ должно быть "/api" в @RequestMapping.
 *
 * Итоговый URL: POST /api/billing/play/verify
 */
@RestController
@RequestMapping("/billing/play") // ✅ CHANGED (убрали /api)
@RequiredArgsConstructor
public class PlayBillingController {

    private final PlayBillingService playBillingService;
    private final CompanyService companyService;

    /**
     * ✅ CHANGED:
     *  packageName берём с сервера (из env/yml), а не из клиента.
     *  Это защита: клиент не сможет подсунуть чужой packageName.
     */
    @Value("${app.play.package-name:}")
    private String serverPackageName; // ➕ ADDED

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody VerifyRequest req, Principal principal) { // ✅ CHANGED (@Valid)
        try {
            if (principal == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
            }

            // ✅ CHANGED: packageName берём с сервера, если задан
            String packageName = (serverPackageName != null && !serverPackageName.isBlank())
                    ? serverPackageName.trim()
                    : req.getPackageName();

            if (packageName == null || packageName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "package_name_missing",
                        "message", "packageName is missing (set app.play.package-name in application.yml/env)"
                ));
            }

            var result = playBillingService.verify(packageName, req.getProductId(), req.getPurchaseToken());
            boolean active = playBillingService.isActive(result);
            long expiry = playBillingService.expiryMillis(result);

            // ✅ Активируем доступ компании (у тебя уже есть этот метод)
            companyService.activateFromGoogle(principal.getName(), req.getProductId(), expiry);

            return ResponseEntity.ok(Map.of(
                    "active", active,
                    "expiryTime", expiry
            ));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", "google_play_verify_failed",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
            ));
        }
    }

    @Data
    public static class VerifyRequest {
        @NotBlank private String productId;
        @NotBlank private String purchaseToken;

        // оставляем поле для совместимости (если serverPackageName не задан)
        private String packageName; // ✅ CHANGED (не обязательное)
    }
}
