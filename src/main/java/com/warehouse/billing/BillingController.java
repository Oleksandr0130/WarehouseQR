package com.warehouse.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;                    // <-- CHECKOUT Session
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;       // <-- CHECKOUT SessionCreateParams
import com.warehouse.model.Company;
import com.warehouse.repository.CompanyRepository;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.stripe.model.InvoiceCollection;
import com.stripe.model.PaymentIntent;
import com.stripe.param.InvoiceListParams;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/billing") // снаружи будет /api/billing/** (context-path=/api)
@RequiredArgsConstructor
public class BillingController {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyService companyService;

    @Value("${app.stripe.price-id}")
    private String priceId; // ДОЛЖЕН быть вида price_..., не prod_...

    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${app.billing.frontend-base-url}")
    private String frontendBase; // например, https://warehouse-qr-app-8adwv.ondigitalocean.app

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

        // === ВАЖНО: статус вычисляем надёжно по датам и флагу,
        //     чтобы фронт сразу получил "ACTIVE" после обновления записи из вебхука.
        String computedStatus = computeStatus(c);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", computedStatus); // TRIAL / ACTIVE / EXPIRED / ...

        if (c.getTrialEnd() != null)          body.put("trialEnd", c.getTrialEnd().toString());
        if (c.getCurrentPeriodEnd() != null)  body.put("currentPeriodEnd", c.getCurrentPeriodEnd().toString());
        body.put("daysLeft", companyService.daysLeft(c));
        body.put("isAdmin", "ROLE_ADMIN".equals(user.getRole()));

        // === Если есть открытый счёт, требующий 3DS/SCA — отдадим ссылку на hosted_invoice_url
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
                    PaymentIntent pi = inv.getPaymentIntentObject();
                    if (pi != null && "requires_action".equals(pi.getStatus())) {
                        String hosted = inv.getHostedInvoiceUrl();
                        if (hosted != null && !hosted.isBlank()) {
                            body.put("pendingInvoiceUrl", hosted);
                        }
                    }
                }
            }
        } catch (Exception ignore) {
            // молча не мешаем статусу, если Stripe временно недоступен
        }

        return ResponseEntity.ok(body);
    }

    /** Аккуратное вычисление статуса по твоей модели Company */
    private String computeStatus(Company c) {
        Instant now = Instant.now();

        // boolean-поле => используем isSubscriptionActive()
        boolean activeFlag = false;
        try {
            activeFlag = c.isSubscriptionActive();
        } catch (Throwable ignore) { /* на всякий случай */ }

        Instant currentEnd = c.getCurrentPeriodEnd();
        Instant trialEnd   = c.getTrialEnd();

        boolean activeByDate = currentEnd != null && currentEnd.isAfter(now);
        boolean trialByDate  = trialEnd   != null && trialEnd.isAfter(now);

        if (activeFlag && activeByDate) return "ACTIVE";
        if (activeByDate)               return "ACTIVE";
        if (trialByDate)                return "TRIAL";
        // если ничего не подходит, считаем истёкшим
        return "EXPIRED";
    }

    // ---------------------- CHECKOUT ----------------------
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

            // 1) Customer для компании (создаём один раз)
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

            // 2) URL'ы возврата
            String successUrl = frontendBase + "/?billing=success";
            String cancelUrl  = frontendBase + "/?billing=cancel";

            // 3) Checkout Session
            SessionCreateParams.PaymentMethodOptions pmOpts =
                    SessionCreateParams.PaymentMethodOptions.builder()
                            .putExtraParam("card[request_three_d_secure]", "any")
                            .build();

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .setPaymentMethodOptions(pmOpts)
                    .putExtraParam("payment_method_collection", "always")
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId) // ОБЯЗАТЕЛЬНО price_...
                                    .setQuantity(1L)
                                    .build()
                    )
                    // опционально, если включён SEPA в Dashboard:
                    // .addPaymentMethodType(SessionCreateParams.PaymentMethodType.SEPA_DEBIT)
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
                case "checkout.session.completed": {
                    var obj = event.getDataObjectDeserializer().getObject();
                    if (obj.isPresent() && obj.get() instanceof Session s) {
                        String subscriptionId = s.getSubscription();
                        if (subscriptionId != null) {
                            Subscription sub = Subscription.retrieve(subscriptionId);
                            String customerId = sub.getCustomer();
                            Long end = sub.getCurrentPeriodEnd();
                            if (customerId != null && end != null) {
                                companyRepository.findByPaymentCustomerId(customerId).ifPresent(c -> {
                                    // Ставим активность и сохраняем период
                                    safeSetActive(c, Instant.ofEpochSecond(end));
                                    companyRepository.save(c);
                                });
                            }
                        }
                    }
                    break;
                }
                case "checkout.session.expired": {
                    // оформление прервано — можно залогировать
                    break;
                }
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
                            // Явно завершаем период — сдвигаем на «сейчас», оставляем триал как есть
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

    /** Безопасно поставить активную подписку и дату окончания — под твою модель Company */
    private void safeSetActive(Company c, Instant currentPeriodEnd) {
        try { c.setSubscriptionActive(true); } catch (Throwable ignore) {}
        try { c.setCurrentPeriodEnd(currentPeriodEnd); } catch (Throwable ignore) {}
        // ВАЖНО: setSubscriptionStatus(...) удалён — у Company статус вычисляемый (@Transient)
        // try { c.setSubscriptionStatus("ACTIVE"); } catch (Throwable ignore) {}
        // По желанию можно обнулить триал:
        // try { c.setTrialEnd(null); } catch (Throwable ignore) {}
    }
}