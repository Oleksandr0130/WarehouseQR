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
import org.springframework.security.access.prepost.PreAuthorize;
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
            return ResponseEntity.badRequest().body("invalid signature");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("bad request");
        }

        try {
            final var type = event.getType();
            log.info("Billing webhook received: type={}", type);

            // --- 1) checkout.session.completed ---
            if ("checkout.session.completed".equals(type)) {
                // не полагаемся на instanceof — делаем надёжный фолбэк
                Session s = event.getDataObjectDeserializer().getObject()
                        .filter(o -> o instanceof Session)
                        .map(o -> (Session) o)
                        .orElseGet(() -> com.stripe.net.ApiResource.GSON.fromJson(
                                event.getData().getObject().toJson(), Session.class));

                handleCheckoutCompleted(s);   // см. метод ниже
                return ResponseEntity.ok("ok");
            }

            // --- 2) payment_intent.succeeded (подстраховка) ---
            if ("payment_intent.succeeded".equals(type)) {
                PaymentIntent pi = event.getDataObjectDeserializer().getObject()
                        .filter(o -> o instanceof PaymentIntent)
                        .map(o -> (PaymentIntent) o)
                        .orElseGet(() -> com.stripe.net.ApiResource.GSON.fromJson(
                                event.getData().getObject().toJson(), PaymentIntent.class));

                // company по customerId; client_reference_id тут нет — ок
                Company c = resolveCompanyByCustomerOrRef(pi.getCustomer(), null);
                if (c == null) {
                    log.error("Billing webhook: company NOT resolved for payment_intent {}", pi.getId());
                    throw new IllegalStateException("Company not resolved for payment_intent " + pi.getId());
                }

                // Валюта / сумма
                String currency = (pi.getCurrency() != null) ? pi.getCurrency().toUpperCase() : "EUR";
                BigDecimal amount = (pi.getAmountReceived() != null)
                        ? BigDecimal.valueOf(pi.getAmountReceived()).movePointLeft(2)
                        : (pi.getAmount() != null ? BigDecimal.valueOf(pi.getAmount()).movePointLeft(2) : BigDecimal.ZERO);
                Instant paidAt = (pi.getCreated() != null) ? Instant.ofEpochSecond(pi.getCreated()) : Instant.now();

                // Фиксируем валюту компании, продлеваем доступ, сохраняем платёж
                activateAndSave(c, currency, amount, paidAt, pi.getId(), "card", null);
                return ResponseEntity.ok("ok");
            }

            // остальные события не интересны
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Webhook handling error", e);
            return ResponseEntity.internalServerError().body("webhook error");
        }
    }

    private void handleCheckoutCompleted(Session s) throws Exception {
        if (!"payment".equalsIgnoreCase(s.getMode()) || !"paid".equalsIgnoreCase(s.getPaymentStatus())) {
            log.info("Checkout completed not a paid one-off. mode={}, status={}", s.getMode(), s.getPaymentStatus());
            return;
        }

        log.info("Billing webhook: session={}, customer={}, client_ref={}", s.getId(), s.getCustomer(), s.getClientReferenceId());

        Company c = (s.getCustomer() != null)
                ? resolveCompanyByCustomerOrRef(s.getCustomer(), s)
                : resolveCompanyByCustomerOrRef(null, s);

        if (c == null) {
            log.error("Billing webhook: company NOT resolved for session={}", s.getId());
            throw new IllegalStateException("Company not resolved for session " + s.getId());
        }

        String currency = (s.getCurrency() != null) ? s.getCurrency().toUpperCase() : "EUR";
        BigDecimal amount = (s.getAmountTotal() != null)
                ? BigDecimal.valueOf(s.getAmountTotal()).movePointLeft(2)
                : BigDecimal.ZERO;
        Instant paidAt = Instant.now();
        String method = "unknown";

        // попробуем обогатиться через PI, но это не критично
        try {
            if (s.getPaymentIntent() != null) {
                PaymentIntent pi = PaymentIntent.retrieve(s.getPaymentIntent());
                if (pi.getCurrency() != null) currency = pi.getCurrency().toUpperCase();
                if (pi.getAmountReceived() != null) amount = BigDecimal.valueOf(pi.getAmountReceived()).movePointLeft(2);
                if (pi.getCreated() != null) paidAt = Instant.ofEpochSecond(pi.getCreated());
                if (pi.getPaymentMethod() != null) {
                    try {
                        PaymentMethod pm = PaymentMethod.retrieve(pi.getPaymentMethod());
                        if (pm != null && pm.getType() != null) method = pm.getType();
                    } catch (Exception ignore) {}
                }
            }
        } catch (Exception enrichErr) {
            log.warn("Failed to enrich from PaymentIntent for session {}", s.getId(), enrichErr);
        }

        activateAndSave(c, currency, amount, paidAt, (s.getPaymentIntent() != null ? s.getPaymentIntent() : s.getId()), method, s);
    }

    private void activateAndSave(Company c, String currency, BigDecimal amount, Instant paidAt,
                                 String txnId, String method, Session sessionOrNull) {
        // зафиксировать валюту
        if (c.getBillingCurrency() == null || c.getBillingCurrency().isBlank()) {
            c.setBillingCurrency(currency);
        }

        // продлить доступ
        Instant now = Instant.now();
        Instant base = (c.getCurrentPeriodEnd() != null && c.getCurrentPeriodEnd().isAfter(now))
                ? c.getCurrentPeriodEnd()
                : now;
        Instant newEnd = base.plus(oneOffExtendDays, ChronoUnit.DAYS);
        safeSetActive(c, newEnd);
        companyRepository.save(c);
        log.info("Billing webhook: activated company id={} until {}", c.getId(), newEnd);

        // запись о платеже (если таблица включена)
        try {
            Payment payment = Payment.builder()
                    .company(c)
                    .provider("stripe")
                    .method(method)
                    .currency(currency)
                    .amount(amount)
                    .status("paid")
                    .transactionId(txnId)
                    .paidAt(paidAt)
                    .periodStart(now)
                    .periodEnd(newEnd)
                    .rawPayload(sessionOrNull != null ? sessionOrNull.toJson() : null)
                    .build();
            paymentRepository.save(payment);
        } catch (Exception saveErr) {
            log.warn("Payment save failed: {}", saveErr.getMessage());
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
