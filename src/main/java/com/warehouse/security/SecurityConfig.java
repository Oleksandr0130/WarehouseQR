// src/main/java/com/warehouse/security/SecurityConfig.java
package com.warehouse.security;

import com.warehouse.billing.SubscriptionService;
import com.warehouse.repository.UserRepository;
import com.warehouse.security.filter.JwtAuthenticationFilter;
import com.warehouse.security.filter.SubscriptionGuardFilter;
import com.warehouse.security.service.JwtTokenProvider;
import com.warehouse.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SubscriptionService subscriptionService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Игнорируем CSRF для вебхука Stripe.
                // Указываем оба варианта: с /api и без — чтобы не упереться в особенности матчинга.
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // Stripe webhook — публично (оба варианта путей)
                        .requestMatchers("/stripe/webhook", "/api/stripe/webhook").permitAll()

                        // аутентификация/регистрация — публично
                        .requestMatchers("/auth/**").permitAll()

                        // статика/фронт (подправь под свой билд, если нужно)
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/static/**",
                                "/favicon.ico",
                                "/manifest.json"
                        ).permitAll()

                        // health/status (если нужен публично)
                        .requestMatchers("/status").permitAll()

                        // всё по биллингу (кроме вебхука) — только для аутентифицированных
                        // ВНИМАНИЕ: Контроллер объявлен как @RequestMapping("/billing"),
                        // а общий context-path=/api, так что фактически это /api/billing/**
                        .requestMatchers("/billing/**", "/api/billing/**").authenticated()

                        // остальные запросы — по умолчанию требуем аутентификацию
                        .anyRequest().authenticated()
                );

        // 1) JWT — ДО стандартного Username/Password фильтра
        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService()),
                UsernamePasswordAuthenticationFilter.class);

        // 2) Guard — ПОСЛЕ JWT, чтобы видеть уже установленный Authentication
        http.addFilterAfter(subscriptionGuardFilter(), JwtAuthenticationFilter.class);

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
