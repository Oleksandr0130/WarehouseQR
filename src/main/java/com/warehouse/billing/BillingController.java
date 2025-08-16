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

import java.time.Instant;
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

        // ВАЖНО: без Map.of(null, ...) — там нельзя null
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
        try {
            var user = userRepository.findByUsername(auth.getName()).orElseThrow();
            if (!"ROLE_ADMIN".equals(user.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "admin_only"));
            }

            Company company = user.getCompany();
            if (company == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "no_company"));
            }

            // 1) Customer на уровне company (создаём один раз)
            String customerId = company.getPaymentCustomerId();
            if (customerId == null) {
                var cp = new CustomerCreateParams.Builder()
                        .setName(company.getName())
                        .putMetadata("companyId", String.valueOf(company.getId()))
                        .build();
                var customer = com.stripe.model.Customer.create(cp);
                customerId = customer.getId();
                company.setPaymentCustomerId(customerId);
                companyRepository.save(company);
            }

            // 2) Checkout Session (SUBSCRIPTION)
            String successUrl = frontendBase + "/?billing=success";
            String cancelUrl  = frontendBase + "/?billing=cancel";

            var params = new SessionCreateParams.Builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addLineItem(
                            new SessionCreateParams.LineItem.Builder()
                                    .setPrice(priceId) // ОБЯЗАТЕЛЬНО price_..., не prod_...
                                    .setQuantity(1L)
                                    .build()
                    )
                    .build();

            Session session = Session.create(params); // это checkout.Session
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

    // ------------------- BILLING PORTAL -------------------
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

            // Billing Portal — используем полные имена, чтобы не конфликтовало с checkout
            com.stripe.param.billingportal.SessionCreateParams portalParams =
                    new com.stripe.param.billingportal.SessionCreateParams.Builder()
                            .setCustomer(company.getPaymentCustomerId())
                            .setReturnUrl(frontendBase)
                            .build();

            com.stripe.model.billingportal.Session portalSession =
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
                                // Лучше достать subscription и взять актуальный current_period_end
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
                            // Можно пометить компанию как неактивную,
                            // но даже без этого фильтр закроет доступ после currentPeriodEnd
                            // c.setSubscriptionActive(false);
                            companyRepository.save(c);
                        });
                    }
                    break;
                }
                default:
                    // ignore other events
            }
        } catch (StripeException e) {
            return ResponseEntity.status(502).body("stripe_error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("server_error");
        }

        return ResponseEntity.ok("ok");
    }
}
