package com.warehouse.config;

import com.warehouse.service.CompanyService;
import com.warehouse.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;

@Component
public class SubscriptionInterceptor implements HandlerInterceptor {

    @Autowired
    private CompanyService companyService;
    @Autowired private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
            throws IOException {
        var user = userService.getCurrentUser();
        var company = user.getCompany();
        Instant now = Instant.now();

        if (now.isAfter(company.getTrialEnd()) && !company.isSubscriptionActive()) {
            res.sendError(HttpStatus.PAYMENT_REQUIRED.value(), "Пробный период завершён, продлите подписку");
            return false;
        }
        return true;
    }
}