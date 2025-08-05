// src/main/java/com/warehouse/security/SecurityConfig.java
package com.warehouse.security;

import com.warehouse.repository.UserRepository;
import com.warehouse.security.filter.JwtAuthenticationFilter;
import com.warehouse.security.service.JwtTokenProvider;
import com.warehouse.service.CompanyService;
import com.warehouse.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserRepository userRepository;
    private final CompanyService companyService; // Для фильтрации подписок
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
        // Настройка цепочки безопасности
        http.csrf(csrf -> csrf.disable()) // Отключаем CSRF
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/register", "/auth/confirm").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService(), companyService),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Обходим использование UserService
        return username -> userRepository.findByUsername(username)
                .map(user -> {
                    if (!user.isEnabled()) throw new RuntimeException("Пожалуйста, подтвердите email.");
                    return org.springframework.security.core.userdetails.User
                            .withUsername(user.getUsername())
                            .password(user.getPassword())
                            .roles(user.getRole().replace("ROLE_", ""))
                            .build();
                })
                .orElseThrow(() -> new RuntimeException("Пользователь с именем " + username + " не найден."));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
