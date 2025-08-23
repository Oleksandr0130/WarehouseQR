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

    /** Пути/префиксы, которые всегда доступны (логин/регистрация, биллинг, профиль, здоровье) */
    private static final Set<String> ALLOW_PREFIXES = Set.of(
            "/auth",
            "/billing/",     // <-- ВСЕ биллинговые ручки через /api
            "/billing/webhook",  // вебхук (если без /api)
            "/users/me",         // аккаунт должен грузиться при истёкшем доступе
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

    private static boolean isAllowedPath(String path) {
        for (String p : ALLOW_PREFIXES) {
            if (path.startsWith(p)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // NB: без contextPath
        final String path = request.getServletPath();
        final String method = request.getMethod();

        // 0) CORS preflight и статика — пропускаем
        if (HttpMethod.OPTIONS.matches(method) || isStaticGet(path, method)) {
            chain.doFilter(request, response);
            return;
        }

        // 1) Разрешённые пути (auth, /api/billing/**, /users/me, /status) — пропускаем
        if (isAllowedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 2) Если не авторизован — пусть дальше решает Security (401/доступ)
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

        // 4) Две независимые проверки; достаточно ЛЮБОЙ true
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
