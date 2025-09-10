package com.warehouse.security;

import com.warehouse.billing.SubscriptionService;
import com.warehouse.repository.UserRepository;
import com.warehouse.security.filter.JwtAuthenticationFilter;
import com.warehouse.security.filter.SubscriptionGuardFilter;
import com.warehouse.security.service.JwtTokenProvider;
import com.warehouse.service.CompanyService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SubscriptionService subscriptionService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, resp, ex) -> resp.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((req, resp, ex) -> resp.sendError(HttpServletResponse.SC_FORBIDDEN))
                )
                .authorizeHttpRequests(auth -> auth
                        // Публичные маршруты аутентификации/подтверждения
                        .requestMatchers("/auth/register", "/auth/confirm", "/confirmation").permitAll()

                        // Публичные Stripe вебхуки и страницы оплаты в вебе
                        .requestMatchers(
                                "/billing/webhook",   // Stripe webhook
                                "/billing/checkout",
                                "/billing/portal",
                                "/billing/status",
                                "/status"
                        ).permitAll()
                        // Если у тебя есть префикс /api на сервере, продублируем правила:
                        .requestMatchers(
                                "/api/billing/webhook",
                                "/api/billing/checkout",
                                "/api/billing/portal",
                                "/api/billing/status",
                                "/api/status"
                        ).permitAll()

                        // ✅ Play Billing verify — ТОЛЬКО для аутентифицированных пользователей
                        .requestMatchers("/billing/play/verify", "/api/billing/play/verify").authenticated()

                        // Остальное — по умолчанию требуем аутентификацию
                        .anyRequest().permitAll()
                )

                // 0) авто-refresh до JWT
                .addFilterBefore(new com.warehouse.security.filter.RefreshTokenFilter(jwtTokenProvider, userDetailsService()),
                        UsernamePasswordAuthenticationFilter.class)
                // 1) обычная JWT-аутентификация
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService()),
                        UsernamePasswordAuthenticationFilter.class)
                // 2) Guard — после того, как контекст уже установлен
                .addFilterAfter(subscriptionGuardFilter(), JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public SubscriptionGuardFilter subscriptionGuardFilter() {
        return new SubscriptionGuardFilter(userRepository, companyService, subscriptionService);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            var user = userRepository.findByUsername(username).orElseThrow();
            if (!user.isEnabled()) throw new RuntimeException("Пожалуйста, подтвердите email");
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .roles(user.getRole().replace("ROLE_", ""))
                    .build();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
