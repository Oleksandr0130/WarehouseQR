package com.warehouse.billing;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.warehouse.service.CompanyService;

import java.security.Principal;
import java.util.Map;

/**
 * Принимает purchaseToken от APK, проверяет у Google и активирует доступ.
 * URL: POST /api/billing/play/verify
 * Требует аутентификацию (настроим в SecurityConfig: authenticated()).
 */
@RestController
@RequestMapping("/api/billing/play")
@RequiredArgsConstructor
public class PlayBillingController {

    private final PlayBillingService playBillingService;
    private final CompanyService companyService; // используй свой сервис/репозиторий домена

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest req, Principal principal) throws Exception {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        var result = playBillingService.verify(req.getPackageName(), req.getProductId(), req.getPurchaseToken());
        boolean active = playBillingService.isActive(result);
        long expiry  = playBillingService.expiryMillis(result);

        // ✅ Активируем доступ пользователю/компании в твоей доменной модели.
        // ЗАМЕНИ на свой метод, если он у тебя называется иначе:
        // Например: companyService.activateSubscription(principal.getName(), req.getProductId(), expiry, "GOOGLE");
        companyService.activateFromGoogle(principal.getName(), req.getProductId(), expiry);

        return ResponseEntity.ok(Map.of(
                "active", active,
                "expiryTime", expiry
        ));
    }

    @Data
    public static class VerifyRequest {
        @NotBlank private String productId;
        @NotBlank private String purchaseToken;
        @NotBlank private String packageName;
    }
}
