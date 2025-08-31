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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/register", "/auth/confirm","/confirmation").permitAll()
                        .requestMatchers(
                                "/auth/**",
                                "/billing/**",
                                "/billing/webhook",
                                "/billing/checkout",
                                "/billing/portal",
                                "/billing/status",
                                "/status"
                        ).permitAll()
                        .anyRequest().permitAll()
                )
                // 1) JWT — ДО username/password фильтра
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService()),
                        UsernamePasswordAuthenticationFilter.class)
                // 2) Guard — ПОСЛЕ JWT, чтобы видеть Authentication
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