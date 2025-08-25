package com.warehouse.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceCollection;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.warehouse.model.Company;
import com.warehouse.repository.CompanyRepository;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/billing") // снаружи будет /api/billing/**
@RequiredArgsConstructor
public class BillingController {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyService companyService;

    /** Recurring (подписка) price_... */
    @Value("${app.stripe.price-id}")
    private String priceId;

    /** One-time (разовая) price_... */
    @Value("${app.stripe.one-time-price-id}")
    private String oneTimePriceId;

    /** Сколько дней доступа выдать после разовой оплаты */
    @Value("${app.billing.one-time-days:30}")
    private int oneTimeDays;

    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${app.billing.frontend-base-url}")
    private String frontendBase; // напр., https://warehouse-qr-app-8adwv.ondigitalocean.app

    // ----------------------- STATUS -----------------------
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
        body.put("status", computedStatus); // TRIAL / ACTIVE / EXPIRED / ...
        if (c.getTrialEnd() != null)         body.put("trialEnd", c.getTrialEnd().toString());
        if (c.getCurrentPeriodEnd() != null) body.put("currentPeriodEnd", c.getCurrentPeriodEnd().toString());
        body.put("daysLeft", companyService.daysLeft(c));
        body.put("isAdmin", "ROLE_ADMIN".equals(user.getRole()));

        // Если есть открытый счёт, требующий SCA — вернём hosted_invoice_url
        try {
            String customerId = c.getPaymentCustomerId();
            if (customerId != null) {
                InvoiceListParams invParams = InvoiceListParams.builder()
                        .setCustomer(customerId)
                        .setLimit(1L)
                        .putExtraParam("status", "open")
                        .addExpand("data.payment_intent")
                        .build();

                InvoiceCollection invoices = Invoice.list(invParams);
                if (invoices != null && invoices.getData() != null && !invoices.getData().isEmpty()) {
                    Invoice inv = invoices.getData().get(0);
                    var pi = inv.getPaymentIntentObject();
                    if (pi != null && "requires_action".equals(pi.getStatus())) {
                        String hosted = inv.getHostedInvoiceUrl();
                        if (hosted != null && !hosted.isBlank()) {
                            body.put("pendingInvoiceUrl", hosted);
                        }
                    }
                }
            }
        } catch (Exception ignore) {}

        return ResponseEntity.ok(body);
    }

    /** Аккуратное вычисление статуса по модели Company */
    private String computeStatus(Company c) {
        Instant now = Instant.now();

        boolean activeFlag = false;
        try { activeFlag = c.isSubscriptionActive(); } catch (Throwable ignore) {}

        Instant currentEnd = c.getCurrentPeriodEnd();
        Instant trialEnd   = c.getTrialEnd();

        boolean activeByDate = currentEnd != null && currentEnd.isAfter(now);
        boolean trialByDate  = trialEnd   != null && trialEnd.isAfter(now);

        if (activeFlag && activeByDate) return "ACTIVE";
        if (activeByDate)               return "ACTIVE";
        if (trialByDate)                return "TRIAL";
        return "EXPIRED";
    }

    // ---------------------- CHECKOUT (подписка) ----------------------
    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(Authentication auth) {
        try {
            var user = userRepository.findByUsername(auth.getName()).orElseThrow();
            if (!"ROLE_ADMIN".equals(user.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "admin_only"));
            }

            Company company = user.getCompany();
            if (company == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "no_company"));
            }

            // 1) Customer
            String customerId = ensureCustomer(company);

            // 2) URL'ы возврата
            String successUrl = frontendBase + "/?billing=success";
            String cancelUrl  = frontendBase + "/?billing=cancel";

            // 3) Checkout Session — ТОЛЬКО recurring priceId
            SessionCreateParams.PaymentMethodOptions pmOpts =
                    SessionCreateParams.PaymentMethodOptions.builder()
                            .putExtraParam("card[request_three_d_secure]", "any")
                            .build();

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)        // включает Google Pay
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.SEPA_DEBIT)  // SEPA автосписание
                    .setPaymentMethodOptions(pmOpts)
                    .putExtraParam("payment_method_collection", "always")
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId) // recurring price_...
                                    .setQuantity(1L)
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);

            return ResponseEntity.ok(Map.of("checkoutUrl", session.getUrl()));
        } catch (StripeException e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", "stripe_error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "server_error",
                    "message", e.getMessage()
            ));
        }
    }

    // ---------------------- CHECKOUT (разовая оплата) ----------------------
    @PostMapping("/checkout-onetime")
    public ResponseEntity<?> createCheckoutOneTime(Authentication auth) {
        try {
            var user = userRepository.findByUsername(auth.getName()).orElseThrow();
            if (!"ROLE_ADMIN".equals(user.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "admin_only"));
            }
            Company company = user.getCompany();
            if (company == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "no_company"));
            }

            String customerId = ensureCustomer(company);

            String successUrl = frontendBase + "/?billing=success";
            String cancelUrl  = frontendBase + "/?billing=cancel";

            // Для Mode.PAYMENT не ставим payment_method_collection
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)  // Google Pay внутри
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.BLIK)  // при условии включения в Dashboard
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(oneTimePriceId) // one-time price_...
                                    .setQuantity(1L)
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);
            return ResponseEntity.ok(Map.of("checkoutUrl", session.getUrl()));
        } catch (StripeException e) {
            return ResponseEntity.status(502).body(Map.of("error", "stripe_error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "server_error", "message", e.getMessage()));
        }
    }

    private String ensureCustomer(Company company) throws StripeException {
        String customerId = company.getPaymentCustomerId();
        if (customerId == null) {
            var cp = CustomerCreateParams.builder()
                    .setName(company.getName())
                    .putMetadata("companyId", String.valueOf(company.getId()))
                    .build();
            var customer = com.stripe.model.Customer.create(cp);
            customerId = customer.getId();
            company.setPaymentCustomerId(customerId);
            companyRepository.save(company);
        }
        return customerId;
    }

    // ------------------- BILLING ПОРТАЛ -------------------
    @GetMapping("/portal")
    public ResponseEntity<?> portal(Authentication auth) {
        try {
            var user = userRepository.findByUsername(auth.getName()).orElseThrow();
            if (!"ROLE_ADMIN".equals(user.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "admin_only"));
            }
            Company company = user.getCompany();
            if (company == null || company.getPaymentCustomerId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error","no_customer"));
            }

            var portalParams =
                    new com.stripe.param.billingportal.SessionCreateParams.Builder()
                            .setCustomer(company.getPaymentCustomerId())
                            .setReturnUrl(frontendBase)
                            .build();

            var portalSession =
                    com.stripe.model.billingportal.Session.create(portalParams);

            return ResponseEntity.ok(Map.of("portalUrl", portalSession.getUrl()));
        } catch (StripeException e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", "stripe_error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "server_error",
                    "message", e.getMessage()
            ));
        }
    }

    // ----------------------- WEBHOOK ----------------------
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestHeader("Stripe-Signature") String signature,
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

                // Checkout завершён
                case "checkout.session.completed": {
                    var obj = event.getDataObjectDeserializer().getObject();
                    if (obj.isPresent() && obj.get() instanceof Session s) {
                        String mode = s.getMode();                   // "subscription" | "payment"
                        String customerId = s.getCustomer();         // cus_...
                        String piId = s.getPaymentIntent();          // pi_... (для mode=payment)

                        System.out.println("[WH] checkout.session.completed mode=" + mode +
                                " customer=" + customerId + " pi=" + piId + " payStatus=" + s.getPaymentStatus());

                        if ("subscription".equals(mode)) {
                            String subscriptionId = s.getSubscription();
                            if (subscriptionId != null) {
                                Subscription sub = Subscription.retrieve(subscriptionId);
                                Long end = sub.getCurrentPeriodEnd();
                                if (customerId != null && end != null) {
                                    companyRepository.findByPaymentCustomerId(customerId).ifPresent(c -> {
                                        safeSetActive(c, Instant.ofEpochSecond(end));
                                        companyRepository.save(c);
                                    });
                                }
                            }
                        } else if ("payment".equals(mode)) {
                            // для one-time уточним сам PaymentIntent (иногда completed приходит до succeeded)
                            if (piId != null) {
                                PaymentIntent pi = PaymentIntent.retrieve(piId);
                                if ("succeeded".equals(pi.getStatus())) {
                                    grantOneTimeAccess(customerId);
                                } else {
                                    System.out.println("[WH] PI not succeeded yet: " + pi.getStatus());
                                }
                            } else {
                                // fallback по статусу сессии
                                if ("paid".equalsIgnoreCase(s.getPaymentStatus())) {
                                    grantOneTimeAccess(customerId);
                                }
                            }
                        }
                    }
                    break;
                }

                // Финальное подтверждение для one-time (APM/BLIK и т.п.)
                case "payment_intent.succeeded": {
                    var obj = event.getDataObjectDeserializer().getObject();
                    if (obj.isPresent() && obj.get() instanceof PaymentIntent pi) {
                        String customerId = pi.getCustomer();
                        System.out.println("[WH] payment_intent.succeeded customer=" + customerId + " pi=" + pi.getId());
                        grantOneTimeAccess(customerId);
                    }
                    break;
                }

                // Подписка обновлена / создана
                case "customer.subscription.created":
                case "customer.subscription.updated": {
                    var obj = event.getDataObjectDeserializer().getObject();
                    if (obj.isPresent() && obj.get() instanceof Subscription sub) {
                        String customerId = sub.getCustomer();
                        Optional<Company> opt = companyRepository.findByPaymentCustomerId(customerId);
                        if (opt.isPresent()) {
                            Company c = opt.get();
                            Long end = sub.getCurrentPeriodEnd();
                            if (end != null) {
                                safeSetActive(c, Instant.ofEpochSecond(end));
                                companyRepository.save(c);
                            }
                        }
                    }
                    break;
                }

                // Очередной инвойс по подписке оплачен
                case "invoice.payment_succeeded": {
                    var obj = event.getDataObjectDeserializer().getObject();
                    if (obj.isPresent() && obj.get() instanceof Invoice invoice) {
                        if (invoice.getCustomer() != null && invoice.getSubscription() != null) {
                            Optional<Company> opt = companyRepository.findByPaymentCustomerId(invoice.getCustomer());
                            if (opt.isPresent()) {
                                Company c = opt.get();
                                Subscription subscription = Subscription.retrieve(invoice.getSubscription());
                                Long end = subscription.getCurrentPeriodEnd();
                                if (end != null) {
                                    safeSetActive(c, Instant.ofEpochSecond(end));
                                    companyRepository.save(c);
                                }
                            }
                        }
                    }
                    break;
                }

                // Подписка отменена / оплата не прошла
                case "invoice.payment_failed":
                case "customer.subscription.deleted": {
                    var obj = event.getDataObjectDeserializer().getObject();
                    String customerId = null;
                    if (obj.isPresent()) {
                        if (obj.get() instanceof Invoice invoice) {
                            customerId = invoice.getCustomer();
                        } else if (obj.get() instanceof Subscription sub) {
                            customerId = sub.getCustomer();
                        }
                    }
                    if (customerId != null) {
                        companyRepository.findByPaymentCustomerId(customerId).ifPresent(c -> {
                            try {
                                if (c.getCurrentPeriodEnd() == null || c.getCurrentPeriodEnd().isAfter(Instant.now())) {
                                    c.setCurrentPeriodEnd(Instant.now().minus(1, ChronoUnit.MINUTES));
                                }
                            } catch (Throwable ignore) {}
                            companyRepository.save(c);
                        });
                    }
                    break;
                }

                default:
                    // ignore
            }
        } catch (StripeException e) {
            return ResponseEntity.status(502).body("stripe_error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("server_error");
        }

        return ResponseEntity.ok("ok");
    }

    /** Безопасно выставляем активность и конец периода */
    private void safeSetActive(Company c, Instant currentPeriodEnd) {
        try { c.setSubscriptionActive(true); } catch (Throwable ignore) {}
        try { c.setCurrentPeriodEnd(currentPeriodEnd); } catch (Throwable ignore) {}
    }

    /** Выдаёт доступ после разовой оплаты */
    private void grantOneTimeAccess(String customerId) {
        if (customerId == null) return;
        companyRepository.findByPaymentCustomerId(customerId).ifPresent(c -> {
            Instant end = Instant.now().plus(oneTimeDays, ChronoUnit.DAYS);
            safeSetActive(c, end);
            try { c.setTrialEnd(null); } catch (Throwable ignore) {}
            companyRepository.save(c);
            System.out.println("[WH] One-time access granted till " + end + " for customer " + customerId);
        });
    }
}
