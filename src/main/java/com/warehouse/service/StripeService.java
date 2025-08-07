package com.warehouse.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class StripeService {
    @Value("${stripe.secret-key}") private String secretKey;

    @PostConstruct
    public void init() { Stripe.apiKey = secretKey; }

    public Customer createCustomer(String email, String name) throws StripeException {
        return Customer.create(Map.of("email", email, "name", name));
    }

    public Subscription createSubscription(String customerId, String priceId) throws StripeException {
        return Subscription.create(Map.of(
                "customer", customerId,
                "items", List.of(Map.of("price", priceId))
        ));
    }
}