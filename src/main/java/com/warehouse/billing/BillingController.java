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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyService companyService;
    private final PaymentRepository paymentRepository; // <-- добавили

    // ДВА one_time Price в Stripe: PLN и EUR
    @Value("${app.stripe.price-id-pln}")
    private String priceIdPln;

    @Value("${app.stripe.price-id-eur}")
    private String priceIdEur;

    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${app.billing.frontend-base-url}")
    private String frontendBase;

    // сколько дней продлеваем доступ за один платёж
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
    // Валюта: ?currency=PLN|EUR -> billingCurrency компании -> Accept-Language -> EUR (по умолчанию)
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
                                    .setPrice(priceId) // берём сумму/валюту из Price
                                    .setQuantity(1L)
                                    .build()
                    );

            // Способы оплаты: карта всегда, BLIK только для PLN; PayPal — при необходимости
            builder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
            if ("PLN".equals(currency)) {
                builder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.BLIK);
            }
            // builder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.PAYPAL);

            Session session = Session.create(builder.build());
            return ResponseEntity.ok(Map.of("checkoutUrl", session.getUrl()));
        } catch (StripeException e) {
            // Фолбэк на случай "No such customer"
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
                        return ResponseEntity.status(502).body(Map.of("error","stripe_error","message", inner.getMessage()));
                    }
                }
            }
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
            switch (event.getType()) {
                case "checkout.session.completed": {
                    var obj = event.getDataObjectDeserializer().getObject();
                    if (obj.isPresent() && obj.get() instanceof Session s) {
                        if ("payment".equalsIgnoreCase(s.getMode()) && "paid".equalsIgnoreCase(s.getPaymentStatus())) {
                            String customerId = s.getCustomer();
                            Company c = (customerId != null)
                                    ? resolveCompanyByCustomerOrRef(customerId, s)
                                    : resolveCompanyByCustomerOrRef(null, s);

                            if (c != null) {
                                if (c.getPaymentCustomerId() == null && s.getCustomer() != null) {
                                    c.setPaymentCustomerId(s.getCustomer());
                                }

                                // --- достаём факт оплаты из PaymentIntent ---
                                String paymentIntentId = s.getPaymentIntent();
                                PaymentIntent pi = (paymentIntentId != null) ? PaymentIntent.retrieve(paymentIntentId) : null;

                                String method = "unknown";
                                String currency = "PLN";
                                BigDecimal amount = BigDecimal.ZERO;
                                Instant paidAt = Instant.now();

                                if (pi != null) {
                                    if (pi.getCurrency() != null) currency = pi.getCurrency().toUpperCase();
                                    if (pi.getAmountReceived() != null) {
                                        amount = BigDecimal.valueOf(pi.getAmountReceived()).movePointLeft(2);
                                    }
                                    if (pi.getCreated() != null) paidAt = Instant.ofEpochSecond(pi.getCreated());
                                    if (pi.getPaymentMethod() != null) {
                                        PaymentMethod pm = PaymentMethod.retrieve(pi.getPaymentMethod());
                                        if (pm != null && pm.getType() != null) method = pm.getType(); // "card", "blik", ...
                                    }
                                }

                                // Зафиксировать валюту компании при первой оплате (убрали лишнюю проверку currency != null)
                                if (c.getBillingCurrency() == null) {
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

                                // --- сохраняем платёж в БД (теперь method/amount/paidAt используются) ---
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
                            }
                        }
                    }
                    break;
                }
                default:
                    // Остальные события игнорируем (инвойсы/подписки нам не нужны)
                    break;
            }
        } catch (Exception e) {
            // залогировать при необходимости
        }

        return ResponseEntity.ok("ok");
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
        // 1) явный параметр
        if ("PLN".equalsIgnoreCase(requested) || "EUR".equalsIgnoreCase(requested)) {
            return requested.toUpperCase();
        }
        // 2) валюта, закреплённая за компанией
        if (company.getBillingCurrency() != null && !company.getBillingCurrency().isBlank()) {
            return company.getBillingCurrency().toUpperCase();
        }
        // 3) Accept-Language подсказывает польский
        String lang = req.getHeader("Accept-Language");
        if (lang != null && lang.toLowerCase().startsWith("pl")) return "PLN";

        // 4) по умолчанию
        return "EUR";
    }

    private String priceIdFor(String currency) {
        return "PLN".equalsIgnoreCase(currency) ? priceIdPln : priceIdEur;
    }
}
