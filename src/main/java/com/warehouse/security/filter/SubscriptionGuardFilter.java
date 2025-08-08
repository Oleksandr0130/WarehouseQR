package com.warehouse.security.filter;

import com.warehouse.model.User;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.CompanyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SubscriptionGuardFilter extends OncePerRequestFilter {

    private final CompanyService companyService;
    private final UserRepository userRepository;

    private static final Set<String> ALLOWLIST = Set.of(
            "/api/auth",           // логин/регистрация/refresh
            "/api/billing/checkout",
            "/api/billing/webhook",
            "/api/billing/portal",
            "/api/billing/status"  // фронту нужно
    );

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();

        // пускаем системные и публичные урлы
        for (String prefix : ALLOWLIST) {
            if (path.startsWith(prefix)) {
                chain.doFilter(req, res);
                return;
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(req, res);
            return;
        }

        String username = auth.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getCompany() == null) {
            chain.doFilter(req, res);
            return;
        }

        var company = user.getCompany();
        if (!companyService.isCompanyAccessAllowed(company)) {
            res.setStatus(402); // Payment Required
            res.setContentType("application/json");
            res.getWriter().write("""
               {"error":"subscription_required",
                "message":"Подписка компании истекла. Продлите, чтобы продолжить.",
                "action":"/api/billing/checkout"}
            """);
            return;
        }

        chain.doFilter(req, res);
    }
}
