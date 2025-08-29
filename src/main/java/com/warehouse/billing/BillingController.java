package com.warehouse.billing;

import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.warehouse.model.Company;
import com.warehouse.model.Payment;
import com.warehouse.repository.CompanyRepository;
import com.warehouse.repository.PaymentRepository;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.CompanyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/billing") // с учетом server.servlet.context-path=/api финальные пути будут /api/billing/**
@RequiredArgsConstructor
public class BillingController {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyService companyService;
    private final PaymentRepository paymentRepository;

    // два one_time Price в Stripe: PLN и EUR
    @Value("${app.stripe.price-id-pln}")
    private String priceIdPln;

    @Value("${app.stripe.price-id-eur}")
    private String priceIdEur;

    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${app.billing.frontend-base-url}")
    private String frontendBase;

    // на сколько дней продлеваем доступ за один платёж
    @Value("${app.billing.oneoff.extend-days:30}")
    private int oneOffExtendDays;

    // ---------------- STATUS ----------------
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

        String computedStatus = computeStatus(c);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", computedStatus);
        if (c.getTrialEnd() != null)         body.put("trialEnd", c.getTrialEnd().toString());
        if (c.getCurrentPeriodEnd() != null) body.put("currentPeriodEnd", c.getCurrentPeriodEnd().toString());
        body.put("daysLeft", companyService.daysLeft(c));
        body.put("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
        // чтобы фронт мог «запомнить» валюту
        try {
            if (c.getBillingCurrency() != null && !c.getBillingCurrency().isBlank()) {
                body.put("billingCurrency", c.getBillingCurrency().toUpperCase());
            }
        } catch (Throwable ignore) {}
        return ResponseEntity.ok(body);
    }

    private String computeStatus(Company c) {
        Instant now = Instant.now();
        boolean activeFlag = false;
        try { activeFlag = c.isSubscriptionActive(); } catch (Throwable ignore) {}
        Instant currentEnd = c.getCurrentPeriodEnd();
        Instant trialEnd   = c.getTrialEnd();

        boolean activeByDate = currentEnd != null && currentEnd.isAfter(now);
        boolean trialByDate  = trialEnd   != null && trialEnd.isAfter(now);

        if (activeFlag && activeByDate) return "ACTIVE";
        if (trialByDate) return "TRIAL";
        if (activeByDate) return "ACTIVE";
        return "EXPIRED";
    }

    // -------------- CHECKOUT (ONE-OFF) --------------
    // Валюта берется в приоритете: ?currency=PLN|EUR -> billingCurrency компании -> Accept-Language -> EUR (по умолчанию)
    @PostMapping("/checkout-oneoff")
    public ResponseEntity<?> createOneOffCheckout(
            Authentication auth,
            HttpServletRequest req,
            @RequestParam(value = "currency", required = false) String currencyParam
    ) {
        var userOpt = Optional.ofNullable(auth)
                .filter(Authentication::isAuthenticated)
                .flatMap(a -> userRepository.findByUsername(a.getName()));

        if (userOpt.isEmpty() || userOpt.get().getCompany() == null) {
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        }

        var user = userOpt.get();
        var company = user.getCompany();

        try {
            String customerId = company.getPaymentCustomerId();
            if (customerId == null) {
                var cp = CustomerCreateParams.builder()
                        .setName(company.getName())
                        .setEmail(user.getEmail())
                        .putMetadata("companyId", String.valueOf(company.getId()))
                        .build();
                var customer = Customer.create(cp);
                customerId = customer.getId();
                company.setPaymentCustomerId(customerId);
                companyRepository.save(company);
            }

            String currency = resolveCurrency(req, company, currencyParam); // "PLN" | "EUR"
            String priceId = priceIdFor(currency);

            String successUrl = frontendBase + "/?billing=success";
            String cancelUrl  = frontendBase + "/?billing=cancel";

            SessionCreateParams.Builder builder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setClientReferenceId(String.valueOf(company.getId()))
                    .putMetadata("companyId", String.valueOf(company.getId()))
                    .putMetadata("currency", currency)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId) // сумму/валюту берём из Price
                                    .setQuantity(1L)
                                    .build()
                    );

            // способы оплаты
            builder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
            if ("PLN".equals(currency)) {
                builder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.BLIK);
            }
            // если нужен PayPal, раскомментируй:
            // builder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.PAYPAL);

            Session session = Session.create(builder.build());
            return ResponseEntity.ok(Map.of("checkoutUrl", session.getUrl()));
        } catch (StripeException e) {
            // фолбэк на case "No such customer"
            if (e instanceof InvalidRequestException ire) {
                final String code  = ire.getCode();
                final String param = ire.getParam();
                final String msg   = ire.getMessage() != null ? ire.getMessage() : "";
                final boolean missingCustomer =
                        ("resource_missing".equals(code) && "customer".equals(param))
                                || msg.contains("No such customer");

                if (missingCustomer) {
                    try {
                        var cp = CustomerCreateParams.builder()
                                .setName(company.getName())
                                .setEmail(userOpt.get().getEmail())
                                .putMetadata("companyId", String.valueOf(company.getId()))
                                .build();
                        var newCustomer = Customer.create(cp);

                        company.setPaymentCustomerId(newCustomer.getId());
                        companyRepository.save(company);

                        String currency = resolveCurrency(req, company, currencyParam);
                        String priceId = priceIdFor(currency);

                        String successUrl = frontendBase + "/?billing=success";
                        String cancelUrl  = frontendBase + "/?billing=cancel";

                        SessionCreateParams.Builder retry = SessionCreateParams.builder()
                                .setMode(SessionCreateParams.Mode.PAYMENT)
                                .setCustomer(newCustomer.getId())
                                .setSuccessUrl(successUrl)
                                .setCancelUrl(cancelUrl)
                                .setClientReferenceId(String.valueOf(company.getId()))
                                .putMetadata("companyId", String.valueOf(company.getId()))
                                .putMetadata("currency", currency)
                                .addLineItem(
                                        SessionCreateParams.LineItem.builder()
                                                .setPrice(priceId)
                                                .setQuantity(1L)
                                                .build()
                                );
                        retry.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
                        if ("PLN".equals(currency)) {
                            retry.addPaymentMethodType(SessionCreateParams.PaymentMethodType.BLIK);
                        }
                        // retry.addPaymentMethodType(SessionCreateParams.PaymentMethodType.PAYPAL);

                        Session session2 = Session.create(retry.build());
                        return ResponseEntity.ok(Map.of("checkoutUrl", session2.getUrl()));
                    } catch (StripeException inner) {
                        log.error("Stripe create customer retry failed", inner);
                        return ResponseEntity.status(502).body(Map.of("error","stripe_error","message", inner.getMessage()));
                    }
                }
            }
            log.error("Stripe error on checkout-oneoff", e);
            return ResponseEntity.status(502).body(Map.of("error","stripe_error","message", e.getMessage()));
        }
    }

    // -------------- WEBHOOK (фиксируем оплату, активируем доступ) --------------
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestHeader("Stripe-Signature") String signature,
                                     @RequestBody String payload) {
        final Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            // неверный whsec — это 400, Stripe не ретраит
            return ResponseEntity.badRequest().body("invalid signature");
        } catch (Exception e) {
            // payload не распарсился как событие — тоже 400
            return ResponseEntity.badRequest().body("bad request");
        }

        try {
            if ("checkout.session.completed".equals(event.getType())) {
                var obj = event.getDataObjectDeserializer().getObject();
                if (obj.isPresent() && obj.get() instanceof Session s) {
                    // интересует только one-off
                    if ("payment".equalsIgnoreCase(s.getMode()) && "paid".equalsIgnoreCase(s.getPaymentStatus())) {
                        String customerId = s.getCustomer();
                        Company c = (customerId != null)
                                ? resolveCompanyByCustomerOrRef(customerId, s)
                                : resolveCompanyByCustomerOrRef(null, s);
                        if (c == null) {
                            throw new IllegalStateException("Company not resolved for session " + s.getId());
                        }

                        if (c.getPaymentCustomerId() == null && s.getCustomer() != null) {
                            c.setPaymentCustomerId(s.getCustomer());
                        }

                        // --- детали оплаты ---
                        String paymentIntentId = s.getPaymentIntent();
                        String method = "unknown";
                        String currency = (s.getCurrency() != null) ? s.getCurrency().toUpperCase() : "EUR";
                        BigDecimal amount = (s.getAmountTotal() != null)
                                ? BigDecimal.valueOf(s.getAmountTotal()).movePointLeft(2)
                                : BigDecimal.ZERO;
                        Instant paidAt = Instant.now();

                        try {
                            if (paymentIntentId != null) {
                                PaymentIntent pi = PaymentIntent.retrieve(paymentIntentId);
                                if (pi.getCurrency() != null) currency = pi.getCurrency().toUpperCase();
                                if (pi.getAmountReceived() != null) {
                                    amount = BigDecimal.valueOf(pi.getAmountReceived()).movePointLeft(2);
                                } else if (pi.getAmount() != null) {
                                    amount = BigDecimal.valueOf(pi.getAmount()).movePointLeft(2);
                                }
                                if (pi.getCreated() != null) paidAt = Instant.ofEpochSecond(pi.getCreated());
                                if (pi.getPaymentMethod() != null) {
                                    try {
                                        PaymentMethod pm = PaymentMethod.retrieve(pi.getPaymentMethod());
                                        if (pm != null && pm.getType() != null) method = pm.getType(); // "card", "blik", ...
                                    } catch (Exception ignore) { /* не критично */ }
                                }
                            }
                        } catch (Exception piErr) {
                            log.warn("Failed to enrich from PaymentIntent, fallback to Session values", piErr);
                        }

                        // зафиксировать валюту за компанией при первой оплате
                        if (c.getBillingCurrency() == null || c.getBillingCurrency().isBlank()) {
                            c.setBillingCurrency(currency);
                        }

                        // --- продлеваем доступ и снимаем TRIAL ---
                        Instant now = Instant.now();
                        Instant base = (c.getCurrentPeriodEnd() != null && c.getCurrentPeriodEnd().isAfter(now))
                                ? c.getCurrentPeriodEnd()
                                : now;
                        Instant newEnd = base.plus(oneOffExtendDays, ChronoUnit.DAYS);
                        safeSetActive(c, newEnd);
                        companyRepository.save(c);

                        // --- опционально сохраняем платёж ---
                        try {
                            Payment payment = Payment.builder()
                                    .company(c)
                                    .provider("stripe")
                                    .method(method)
                                    .currency(currency)
                                    .amount(amount)
                                    .status("paid")
                                    .transactionId(paymentIntentId != null ? paymentIntentId : s.getId())
                                    .paidAt(paidAt)
                                    .periodStart(now)
                                    .periodEnd(newEnd)
                                    .rawPayload(s.toJson())
                                    .build();
                            paymentRepository.save(payment);
                        } catch (Exception saveErr) {
                            log.warn("Payment save failed (access already extended): {}", saveErr.getMessage());
                        }
                    }
                }
            }
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            // ВАЖНО: внутренняя ошибка → 500, чтобы Stripe ретраил и мы не потеряли событие
            log.error("Webhook handling error", e);
            return ResponseEntity.internalServerError().body("webhook error");
        }
    }

    // ---------- helpers ----------
    private Company resolveCompanyByCustomer(String customerId) {
        return companyRepository.findByPaymentCustomerId(customerId).orElse(null);
    }

    private Company resolveCompanyByCustomerOrRef(String customerId, Session s) throws StripeException {
        Company c = null;
        if (customerId != null) {
            c = resolveCompanyByCustomer(customerId);
            if (c != null) return c;

            Customer cust = Customer.retrieve(customerId);
            if (cust != null && cust.getMetadata() != null) {
                String metaCompanyId = cust.getMetadata().get("companyId");
                if (metaCompanyId != null) {
                    try {
                        c = companyRepository.findById(Long.parseLong(metaCompanyId)).orElse(null);
                        if (c != null && c.getPaymentCustomerId() == null) {
                            c.setPaymentCustomerId(customerId);
                            companyRepository.save(c);
                        }
                    } catch (NumberFormatException ignore) {}
                }
            }
        }

        if (c == null && s != null) {
            String ref = s.getClientReferenceId();
            if (ref != null) {
                try {
                    c = companyRepository.findById(Long.parseLong(ref)).orElse(null);
                    if (c != null && customerId != null && c.getPaymentCustomerId() == null) {
                        c.setPaymentCustomerId(customerId);
                        companyRepository.save(c);
                    }
                } catch (NumberFormatException ignore) {}
            }
            if (c == null && s.getMetadata() != null) {
                String metaCompanyId = s.getMetadata().get("companyId");
                if (metaCompanyId != null) {
                    try {
                        c = companyRepository.findById(Long.parseLong(metaCompanyId)).orElse(null);
                        if (c != null && customerId != null && c.getPaymentCustomerId() == null) {
                            c.setPaymentCustomerId(customerId);
                            companyRepository.save(c);
                        }
                    } catch (NumberFormatException ignore) {}
                }
            }
        }
        return c;
    }

    private void safeSetActive(Company c, Instant currentPeriodEnd) {
        try { c.setSubscriptionActive(true); } catch (Throwable ignore) {}
        try { c.setCurrentPeriodEnd(currentPeriodEnd); } catch (Throwable ignore) {}
        try { c.setTrialEnd(null); } catch (Throwable ignore) {}
    }

    // --- выбор валюты и прайса ---
    private String resolveCurrency(HttpServletRequest req, Company company, String requested) {
        if ("PLN".equalsIgnoreCase(requested) || "EUR".equalsIgnoreCase(requested)) {
            return requested.toUpperCase();
        }
        if (company.getBillingCurrency() != null && !company.getBillingCurrency().isBlank()) {
            return company.getBillingCurrency().toUpperCase();
        }
        String lang = req.getHeader("Accept-Language");
        if (lang != null && lang.toLowerCase().startsWith("pl")) return "PLN";
        return "EUR";
    }

    private String priceIdFor(String currency) {
        return "PLN".equalsIgnoreCase(currency) ? priceIdPln : priceIdEur;
    }
}
