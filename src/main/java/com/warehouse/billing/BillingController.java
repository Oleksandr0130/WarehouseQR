package com.warehouse.billing;

import com.warehouse.model.User;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final UserRepository userRepository;
    private final CompanyService companyService;

    /** Статус для фронта */
    @GetMapping("/status")
    public ResponseEntity<?> status(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.ok(Map.of("status", "ANON"));
        }

        var user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.ok(Map.of("status", "NO_COMPANY"));
        }

        var c = user.getCompany();

        // ВАЖНО: Map.of нельзя использовать с null -> используем LinkedHashMap
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", c.getSubscriptionStatus()); // TRIAL/ACTIVE/EXPIRED
        if (c.getTrialEnd() != null) {
            body.put("trialEnd", c.getTrialEnd());
        }
        if (c.getCurrentPeriodEnd() != null) {
            body.put("currentPeriodEnd", c.getCurrentPeriodEnd());
        }
        body.put("daysLeft", companyService.daysLeft(c)); // всегда число
        body.put("isAdmin", "ROLE_ADMIN".equals(user.getRole()));

        return ResponseEntity.ok(body);
    }

    /** Создаём «сессию оплаты». Пока фейковый URL — страницу настроишь позже (Stripe/CloudPayments/…)
     * Возвращаем ссылку, на которую нужно редиректнуть пользователя.
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(Authentication auth) {
        var user = userRepository.findByUsername(auth.getName()).orElseThrow();
        if (!"ROLE_ADMIN".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "admin_only"));

        // TODO: тут интеграция с реальным провайдером. Ниже — имитация:
        String fakePaymentUrl = "/billing/pay"; // сделай простую страницу с UI

        return ResponseEntity.ok(Map.of("checkoutUrl", fakePaymentUrl));
    }

    /** Вебхук провайдера: подтверждаем оплату и продлеваем период на месяц */
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody Map<String,Object> payload) {
        // TODO: проверить подпись провайдера и вытащить companyId / customerId
        Long companyId = Long.valueOf(String.valueOf(payload.getOrDefault("companyId", "0")));
        // найдите компанию через репозиторий (добавь CompanyRepository в этот контроллер при нужде)

        // Пример логики: активируем и ставим paid до +1 месяц от сегодня
        // company.setSubscriptionActive(true);
        // company.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        // companyRepository.save(company);

        return ResponseEntity.ok("ok");
    }

    /** Ссылка в платёжный кабинет — по провайдеру */
    @GetMapping("/portal")
    public ResponseEntity<?> portal(Authentication auth) {
        // TODO: для Stripe здесь дергаем Billing Portal
        return ResponseEntity.status(302).location(URI.create("/billing/portal-ui")).build();
    }

    /** Временная ручка для теста «оплаты» без провайдера — можно удалить в проде. */
    @PostMapping("/debug/activate")
    public ResponseEntity<?> debugActivate(Authentication auth) {
        var user = userRepository.findByUsername(auth.getName()).orElseThrow();
        var c = user.getCompany();
        c.setSubscriptionActive(true);
        c.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        // companyRepository.save(c) — если CompanyService хранит репозиторий — сохрани.
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
