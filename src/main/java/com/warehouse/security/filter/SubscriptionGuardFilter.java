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

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final SubscriptionService subscriptionService;

    private static final Set<String> ALLOW_PREFIXES = Set.of(
            "/auth",             // все под /auth/**
            "/billing/webhook",  // вебхук Stripe (если без /api)
            "/users/me",         // профиль нужен для аккаунта
            "/status"            // health
    );

    private static boolean isStaticGet(String path, String method) {
        if (!HttpMethod.GET.matches(method)) return false;
        return path.startsWith("/assets/") || path.startsWith("/static/")
                || path.equals("/") || path.equals("/index.html")
                || path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".map")
                || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".svg")
                || path.endsWith(".ico") || path.endsWith(".woff") || path.endsWith(".woff2");
    }

    private static boolean startsWithAny(String path, String... prefixes) {
        for (String p : prefixes) if (path.startsWith(p)) return true;
        return false;
    }

    /** Нормализуем путь: убираем contextPath (часто это "/api") */
    private static String normalizedPath(HttpServletRequest req) {
        String uri = req.getRequestURI();   // напр. /api/billing/status
        String ctx = req.getContextPath();  // "" или "/api"
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length()); // -> /billing/status
        }
        return uri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        final String path   = normalizedPath(request);
        final String method = request.getMethod();

        // 0) CORS preflight + статика
        if (HttpMethod.OPTIONS.matches(method) || isStaticGet(path, method)) {
            chain.doFilter(request, response);
            return;
        }

        // 1) ВСЕ биллинговые ручки пропускаем ВСЕГДА (и /billing/, и /api/billing/)
        //    + allowlist (auth, webhook, профиль, health)
        if (startsWithAny(path, "/billing/", "/api/billing/")
                || startsWithAny(path, ALLOW_PREFIXES.toArray(new String[0]))) {
            chain.doFilter(request, response);
            return;
        }

        // 2) Неавторизованных отдаём на отработку Security (401)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        // 3) Пользователь/компания
        String username = auth.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getCompany() == null) {
            chain.doFilter(request, response);
            return;
        }

        // 4) Доступ активен, если ЛЮБОЙ источник говорит "да":
        //    либо CompanyService, либо /api/billing/status (через SubscriptionService)
        boolean activeByCompany = companyService.isCompanyAccessAllowed(user.getCompany());
        boolean activeByBilling = subscriptionService.hasActiveAccess(request);
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
