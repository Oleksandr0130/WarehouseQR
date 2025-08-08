package com.warehouse.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private boolean enabled;

    private boolean subscriptionActive;

    private Instant trialStart;
    private Instant trialEnd;

    private Instant currentPeriodEnd; // дата конца платного периода (из Stripe)

    @Column(name = "payment_customer_id")
    private String paymentCustomerId; // Stripe Customer ID

    // --- getters/setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() {return name;}
    public void setName(String name) { this.name = name; }

    public boolean isEnabled() {return enabled;}
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isSubscriptionActive() { return subscriptionActive; }
    public void setSubscriptionActive(boolean subscriptionActive) { this.subscriptionActive = subscriptionActive; }

    public Instant getTrialStart() { return trialStart; }
    public void setTrialStart(Instant trialStart) { this.trialStart = trialStart; }

    public Instant getTrialEnd() { return trialEnd; }
    public void setTrialEnd(Instant trialEnd) { this.trialEnd = trialEnd; }

    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(Instant currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }

    public String getPaymentCustomerId() { return paymentCustomerId; }
    public void setPaymentCustomerId(String paymentCustomerId) { this.paymentCustomerId = paymentCustomerId; }

    @Transient
    public String getSubscriptionStatus() {
        // ACTIVE — если оплата активна и сейчас до конца оплаченного периода
        // TRIAL — если идёт триал
        // иначе EXPIRED
        Instant now = Instant.now();
        if (subscriptionActive && currentPeriodEnd != null && now.isBefore(currentPeriodEnd)) {
            return "ACTIVE";
        }
        if (trialEnd != null && now.isBefore(trialEnd)) {
            return "TRIAL";
        }
        return "EXPIRED";
    }
}
