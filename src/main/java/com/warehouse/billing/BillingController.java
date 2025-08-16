package com.warehouse.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.warehouse.model.Company;
import com.warehouse.repository.CompanyRepository;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/billing") // итоговый URL: /api/billing/**
@RequiredArgsConstructor
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyService companyService;

    @Value("${app.stripe.price-id}")
    private String priceId; // ОБЯЗАТЕЛЬНО вида price_...

    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;

    // FRONT и BACK берём из настроек (env), см. application.yml
    @Value("${app.billing.frontend-base-url}")
    private String frontendBase; // напр. https://warehouse-qr-app-8adwv.ondigitalocean.app

    @Value("${app.billing.backend-base-url}")
    private String backendBase;  // напр. https://warehouse-qr-app-8adwv.ondigitalocean.app/api

    // ----------------------- STATUS -----------------------
    @GetMapping("/status")
    public ResponseEntity<?> status(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
        }

        var userOpt = userRepository.findByEmail(auth.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "user_not_found"));
        }
        var user = userOpt.get();
        if (user == null || user.getCompany() == null) {
            return ResponseEntity.ok(Map.of("status", "NO_COMPANY"));
        }

        var c = user.getCompany();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", c.getSubscriptionStatus()); // TRIAL / ACTIVE / EXPIRED
        if (c.getTrialEnd() != null) body.put("trialEnd", c.getTrialEnd());
        if (c.getCurrentPeriodEnd() != null) body.put("currentPeriodEnd", c.getCurrentPeriodEnd());
        body.put("daysLeft", companyService.daysLeft(c));
        body.put("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
        return ResponseEntity.ok(body);
    }

    // ---------------------- CHECKOUT ----------------------
    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(Authentication auth) {
        long t0 = System.currentTimeMillis();
        log.info("billing/checkout: start");

        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
            }

            var userOpt = userRepository.findByEmail(auth.getName());
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "user_not_found"));
            }
            var user = userOpt.get();

            if (!"ROLE_ADMIN".equals(user.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "admin_only"));
            }

            Company company = user.getCompany();
            if (company == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "no_company"));
            }

            // 1) Stripe Customer
            String customerId = company.getPaymentCustomerId();
            if (customerId == null) {
                log.info("billing/checkout: creating Stripe customer for company {}", company.getId());
                var cp = new CustomerCreateParams.Builder()
                        .setName(company.getName())
                        .putMetadata("companyId", String.valueOf(company.getId()))
                        .build();
                var customer = com.stripe.model.Customer.create(cp);
                customerId = customer.getId();
                company.setPaymentCustomerId(customerId);
                companyRepository.save(company);
                log.info("billing/checkout: customer created {}", customerId);
            }

            // 2) Checkout Session (SUBSCRIPTION)
            String backend = backendBase.replaceAll("/$", "");
            String successUrl = backend + "/billing/success";
            String cancelUrl  = backend + "/billing/cancel";

            log.info("billing/checkout: creating session (priceId={}, success={}, cancel={})",
                    priceId, successUrl, cancelUrl);

            var params = new SessionCreateParams.Builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addLineItem(
                            new SessionCreateParams.LineItem.Builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);
            log.info("billing/checkout: session {} created in {} ms",
                    session.getId(), (System.currentTimeMillis() - t0));

            return ResponseEntity.ok(Map.of(
                    "id", session.getId(),
                    "url", session.getUrl()
            ));

        } catch (Exception e) {
            log.error("billing/checkout: server error", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "server_error",
                    "message", e.getMessage()
            ));
        }
    }

    // ------------------- ПОРТАЛ ОПЛАТ -------------------
    @PostMapping("/portal")
    public ResponseEntity<?> portal(Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
            }
            var user = userRepository.findByEmail(auth.getName()).orElse(null);
            if (user == null) return ResponseEntity.status(404).body(Map.of("error", "user_not_found"));

            if (!"ROLE_ADMIN".equals(user.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "admin_only"));
            }

            Company company = user.getCompany();
            if (company == null || company.getPaymentCustomerId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error","no_customer"));
            }

            var portalParams = new com.stripe.param.billingportal.SessionCreateParams.Builder()
                    .setCustomer(company.getPaymentCustomerId())
                    .setReturnUrl(frontendBase.replaceAll("/$", "") + "/account")
                    .build();

            var portalSession = com.stripe.model.billingportal.Session.create(portalParams);
            return ResponseEntity.ok(Map.of("url", portalSession.getUrl()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------- REDIRECTS -------------------
    @GetMapping("/cancel")
    public ResponseEntity<Void> cancel() {
        String target = frontendBase.replaceAll("/$", "") + "/account";
        return ResponseEntity.status(302).header("Location", target).build();
    }

    @GetMapping("/success")
    public ResponseEntity<Void> success() {
        String target = frontendBase.replaceAll("/$", "") + "/account";
        return ResponseEntity.status(302).header("Location", target).build();
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
                case "invoice.payment_succeeded": {
                    var invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (invoice != null) {
                        String customerId = invoice.getCustomer();
                        Optional<Company> opt = companyRepository.findByPaymentCustomerId(customerId);
                        if (opt.isPresent()) {
                            Company c = opt.get();
                            // оставляем твой исходный код без изменений, только интеграция webhook
                            Instant periodEnd = invoice.getLines().getData().isEmpty() ? null :
                                    Instant.ofEpochSecond(invoice.getLines().getData().get(0).getPeriod().getEnd());
                            c.setCurrentPeriodEnd(periodEnd);
                            companyRepository.save(c);
                        }
                    }
                    break;
                }
                case "customer.subscription.deleted": {
                    var sub = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (sub != null) {
                        Optional<Company> opt = companyRepository.findByPaymentCustomerId(sub.getCustomer());
                        if (opt.isPresent()) {
                            Company c = opt.get();
                            // оставляем твой исходный код без изменений, только интеграция webhook
                            companyRepository.save(c);
                        }
                    }
                    break;
                }
                default:
                    // ignore others
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("server_error");
        }

        return ResponseEntity.ok("ok");
    }
}
