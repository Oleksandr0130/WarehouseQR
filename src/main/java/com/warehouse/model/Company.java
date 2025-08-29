// Company.java
package com.warehouse.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "companies", indexes = {
        @Index(name = "idx_company_identifier", columnList = "identifier", unique = true)
})
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private boolean enabled;

    private boolean subscriptionActive;

    private Instant trialStart;
    private Instant trialEnd;

    private Instant currentPeriodEnd;

    @Column(name = "payment_customer_id")
    private String paymentCustomerId;

    // ФИКСИРУЕМ валюту биллинга за компанией (PLN | EUR)
    @Column(name = "billing_currency", length = 3)
    private String billingCurrency;

    // >>> НОВОЕ ПОЛЕ <<<
    @Column(name = "identifier", unique = true, length = 64, nullable = false)
    private String identifier;

    // --- getters / setters ---

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

    public String getBillingCurrency() { return billingCurrency; }
    public void setBillingCurrency(String billingCurrency) { this.billingCurrency = billingCurrency; }

    // >>> НОВЫЕ ГЕТТЕР/СЕТТЕР <<<
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    @Transient
    public String getSubscriptionStatus() {
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
