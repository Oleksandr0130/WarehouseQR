package com.warehouse.security.filter;

import com.warehouse.billing.SubscriptionService;
import com.warehouse.model.User;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.CompanyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@RequiredArgsConstructor
public class SubscriptionGuardFilter extends OncePerRequestFilter {

//    private final UserRepository userRepository;
//    private final CompanyService companyService;
//
//    private static final Set<String> ALLOWLIST = Set.of(
//            "/auth", "/billing/checkout", "/billing/webhook", "/billing/portal", "/billing/status"
//    );
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain chain) throws ServletException, IOException {
//
//        String path = request.getServletPath(); // важно: без /api
//
//        // Разрешённые пути (auth, billing)
//        for (String prefix : ALLOWLIST) {
//            if (path.startsWith(prefix)) {
//                chain.doFilter(request, response);
//                return;
//            }
//        }
//
//        // Если не авторизован — пропускаем (пусть отработает Security)
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//        if (auth == null || !auth.isAuthenticated()) {
//            chain.doFilter(request, response);
//            return;
//        }
//
//        String username = auth.getName();
//        User user = userRepository.findByUsername(username).orElse(null);
//        if (user == null || user.getCompany() == null) {
//            chain.doFilter(request, response);
//            return;
//        }
//
//        // Проверка доступа компании
//        if (!companyService.isCompanyAccessAllowed(user.getCompany())) {
//            response.setStatus(402); // Payment Required
//            response.setContentType("application/json; charset=UTF-8");
//            response.getWriter().write("{\"error\":\"payment_required\",\"message\":\"subscription_expired\"}");
//            return;
//        }
//
//        chain.doFilter(request, response);
//    }

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final SubscriptionService subscriptionService;

    /** Пути, доступные без активной подписки (логин/регистрация/биллинг/статусы) */
    private static final Set<String> ALLOWLIST = Set.of(
            "/auth",                // всё под /auth/**
            "/billing/checkout",
            "/billing/webhook",
            "/billing/portal",
            "/billing/status",
            "/status"               // на скрине именно этот путь был 200
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getServletPath(); // без /api

        // 1) Разрешённые пути (auth/billing/status/статические файлы) — пропускаем
        for (String prefix : ALLOWLIST) {
            if (path.startsWith(prefix)) {
                chain.doFilter(request, response);
                return;
            }
        }
        if (HttpMethod.GET.matches(request.getMethod())) {
            if (path.startsWith("/assets/") || path.startsWith("/static/")
                    || path.equals("/") || path.equals("/index.html")
                    || path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".map")
                    || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".svg")
                    || path.endsWith(".ico") || path.endsWith(".woff") || path.endsWith(".woff2")) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 2) Если не авторизован — дальше разберётся Security (401/контроллер)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        // 3) Достаём пользователя и компанию
        String username = auth.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getCompany() == null) {
            chain.doFilter(request, response);
            return;
        }

        // 4) Две независимые проверки активности — достаточно ЛЮБОЙ true
        boolean activeByCompany = companyService.isCompanyAccessAllowed(user.getCompany());
        boolean activeByBilling  = subscriptionService.hasActiveAccess(request);
        boolean active = activeByCompany || activeByBilling;

        if (!active) {
            response.setStatus(402); // Payment Required
            response.setHeader("X-Subscription-Expired", "true");
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\":\"payment_required\",\"message\":\"subscription_expired\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
