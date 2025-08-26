package com.warehouse.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
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
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyService companyService;

    @Value("${app.stripe.price-id}")
    private String priceId;

    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${app.billing.frontend-base-url}")
    private String frontendBase;

    @Value("${app.billing.oneoff.amount-pln:9900}")
    private Long oneOffAmountPln; // 99.00 PLN

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

        // если есть инвойс, требующий 3DS/SCA — отдадим ссылку
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
        } catch (Exception ignore) { /* не мешаем статусу */ }

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

    // -------------- CHECKOUT (SUBSCRIPTION) --------------
    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(Authentication auth) {
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
                        .putMetadata("companyId", String.valueOf(company.getId()))
                        .build();
                var customer = Customer.create(cp);
                customerId = customer.getId();
                company.setPaymentCustomerId(customerId);
                companyRepository.save(company);
            }

            String successUrl = frontendBase + "/?billing=success";
            String cancelUrl  = frontendBase + "/?billing=cancel";

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
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build()
                    )
                    .setClientReferenceId(String.valueOf(company.getId()))
                    .putMetadata("companyId", String.valueOf(company.getId()))
                    .build();

            Session session = Session.create(params);
            return ResponseEntity.ok(Map.of("checkoutUrl", session.getUrl()));
        } catch (StripeException e) {
            return ResponseEntity.status(502).body(Map.of("error", "stripe_error", "message", e.getMessage()));
        }
    }

    // -------------- CHECKOUT (ONE-OFF: BLIK/P24/CARD, PLN) --------------
    @PostMapping("/checkout-oneoff")
    public ResponseEntity<?> createOneOffCheckout(Authentication auth) {
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
                        .putMetadata("companyId", String.valueOf(company.getId()))
                        .build();
                var customer = Customer.create(cp);
                customerId = customer.getId();
                company.setPaymentCustomerId(customerId);
                companyRepository.save(company);
            }

            String successUrl = frontendBase + "/?billing=success";
            String cancelUrl  = frontendBase + "/?billing=cancel";

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setClientReferenceId(String.valueOf(company.getId()))
                    .putMetadata("companyId", String.valueOf(company.getId()))
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.BLIK) // BLIK
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.P24)  // Przelewy24
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                            .setCurrency("pln")
                                            .setUnitAmount(oneOffAmountPln)
                                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                    .setName("Manual extension (one-off, PLN)")
                                                    .build())
                                            .build())
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);
            return ResponseEntity.ok(Map.of("checkoutUrl", session.getUrl()));
        } catch (StripeException e) {
            return ResponseEntity.status(502).body(Map.of("error","stripe_error","message", e.getMessage()));
        }
    }

    // -------------- WEBHOOK --------------
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
                        // SUBSCRIPTION checkout
                        if ("subscription".equalsIgnoreCase(s.getMode())) {
                            String subscriptionId = s.getSubscription();
                            if (subscriptionId != null) {
                                Subscription sub = Subscription.retrieve(subscriptionId);
                                String customerId = sub.getCustomer();
                                Long end = sub.getCurrentPeriodEnd();
                                if (customerId != null && end != null) {
                                    Company c = resolveCompanyByCustomerOrRef(customerId, s);
                                    if (c != null) {
                                        if (c.getPaymentCustomerId() == null) c.setPaymentCustomerId(customerId);
                                        safeSetActive(c, Instant.ofEpochSecond(end));
                                        companyRepository.save(c);
                                    }
                                }
                            }
                        }
                        // ONE-OFF (например, BLIK/Przelewy24)
                        else if ("payment".equalsIgnoreCase(s.getMode())) {
                            if ("paid".equalsIgnoreCase(s.getPaymentStatus())) {
                                String customerId = s.getCustomer();
                                Company c = (customerId != null)
                                        ? resolveCompanyByCustomerOrRef(customerId, s)
                                        : resolveCompanyByCustomerOrRef(null, s);
                                if (c != null) {
                                    if (c.getPaymentCustomerId() == null && s.getCustomer() != null) {
                                        c.setPaymentCustomerId(s.getCustomer());
                                    }
                                    Instant now = Instant.now();
                                    Instant base = (c.getCurrentPeriodEnd() != null && c.getCurrentPeriodEnd().isAfter(now))
                                            ? c.getCurrentPeriodEnd()
                                            : now;
                                    Instant newEnd = base.plus(oneOffExtendDays, ChronoUnit.DAYS);
                                    safeSetActive(c, newEnd); // обнуляет trial внутри
                                    companyRepository.save(c);
                                }
                            }
                        }
                    }
                    break;
                }

                case "invoice.payment_succeeded": {
                    var obj = event.getDataObjectDeserializer().getObject();
                    if (obj.isPresent() && obj.get() instanceof Invoice invoice) {
                        String customerId = invoice.getCustomer();
                        Long end = null;
                        if (invoice.getSubscription() != null) {
                            Subscription sub = Subscription.retrieve(invoice.getSubscription());
                            end = sub.getCurrentPeriodEnd();
                        } else if (invoice.getPeriodEnd() != null) {
                            end = invoice.getPeriodEnd();
                        }
                        if (customerId != null && end != null) {
                            Company c = resolveCompanyByCustomer(customerId);
                            if (c == null) {
                                c = resolveCompanyByCustomerOrRef(customerId, null);
                            }
                            if (c != null) {
                                safeSetActive(c, Instant.ofEpochSecond(end));
                                companyRepository.save(c);
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
                    // игнорируем прочие
                    break;
            }
        } catch (Exception e) {
            // залогируйте если нужно
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
}
