package com.warehouse.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
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
            return ResponseEntity.ok(Map.of("status", "ANON"));
        }

        var user = userRepository.findByUsername(auth.getName()).orElse(null);
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
                log.warn("billing/checkout: unauthorized");
                return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
            }

            var user = userRepository.findByUsername(auth.getName()).orElseThrow();
            if (!"ROLE_ADMIN".equals(user.getRole())) {
                log.warn("billing/checkout: forbidden (not admin)");
                return ResponseEntity.status(403).body(Map.of("error", "admin_only"));
            }

            Company company = user.getCompany();
            if (company == null) {
                log.warn("billing/checkout: no_company");
                return ResponseEntity.badRequest().body(Map.of("error", "no_company"));
            }

            // 1) Customer на уровне company (создаём один раз)
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
            // CALLBACK-и → на БЭКЕНД, который потом редиректит на фронт.
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
                                    .setPrice(priceId) // ВАЖНО: price_..., не prod_...
                                    .setQuantity(1L)
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);
            log.info("billing/checkout: session {} created in {} ms",
                    session.getId(), (System.currentTimeMillis() - t0));

            return ResponseEntity.ok(Map.of("checkoutUrl", session.getUrl()));

        } catch (StripeException e) {
            log.error("billing/checkout: Stripe error", e);
            return ResponseEntity.status(502).body(Map.of(
                    "error", "stripe_error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("billing/checkout: server_error", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "server_error",
                    "message", e.getMessage()
            ));
        }
    }

    // ------------------- BILLING PORTAL -------------------
    @GetMapping("/portal")
    public ResponseEntity<?> portal(Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
            }
            var user = userRepository.findByUsername(auth.getName()).orElseThrow();
            if (!"ROLE_ADMIN".equals(user.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "admin_only"));
            }

            Company company = user.getCompany();
            if (company == null || company.getPaymentCustomerId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error","no_customer"));
            }

            var portalParams = new com.stripe.param.billingportal.SessionCreateParams.Builder()
                    .setCustomer(company.getPaymentCustomerId())
                    .setReturnUrl(frontendBase.replaceAll("/$", ""))
                    .build();

            var portalSession = com.stripe.model.billingportal.Session.create(portalParams);
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
                case "customer.subscription.created":
                case "customer.subscription.updated": {
                    var obj = event.getDataObjectDeserializer().getObject();
                    if (obj.isPresent() && obj.get() instanceof Subscription sub) {
                        String customerId = sub.getCustomer();
                        Optional<Company> opt = companyRepository.findByPaymentCustomerId(customerId);
                        if (opt.isPresent()) {
                            Company c = opt.get();
                            Long end = sub.getCurrentPeriodEnd(); // epoch seconds
                            if (end != null) {
                                c.setSubscriptionActive(true);
                                c.setCurrentPeriodEnd(Instant.ofEpochSecond(end));
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
                                    c.setSubscriptionActive(true);
                                    c.setCurrentPeriodEnd(Instant.ofEpochSecond(end));
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
                            // при желании можно пометить как неактивную
                            companyRepository.save(c);
                        });
                    }
                    break;
                }
                default:
                    // ignore others
            }
        } catch (StripeException e) {
            return ResponseEntity.status(502).body("stripe_error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("server_error");
        }

        return ResponseEntity.ok("ok");
    }
}
