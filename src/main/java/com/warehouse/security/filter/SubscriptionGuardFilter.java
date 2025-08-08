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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@RequiredArgsConstructor
public class SubscriptionGuardFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final CompanyService companyService;

    private static final Set<String> ALLOWLIST = Set.of(
            "/auth", "/billing/checkout", "/billing/webhook", "/billing/portal", "/billing/status"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getServletPath(); // важно: без /api

        // Разрешённые пути (auth, billing)
        for (String prefix : ALLOWLIST) {
            if (path.startsWith(prefix)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Если не авторизован — пропускаем (пусть отработает Security)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        String username = auth.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getCompany() == null) {
            chain.doFilter(request, response);
            return;
        }

        // Проверка доступа компании
        if (!companyService.isCompanyAccessAllowed(user.getCompany())) {
            response.setStatus(402); // Payment Required
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\":\"payment_required\",\"message\":\"subscription_expired\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
