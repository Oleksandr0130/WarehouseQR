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
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

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
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // публичные эндпоинты аутентификации/подтверждения
                        .requestMatchers("/auth/register", "/auth/confirm", "/confirmation").permitAll()
                        .requestMatchers("/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                        // вебхук биллинга должен быть публичным
                        .requestMatchers("/billing/webhook").permitAll()
                        // статика / корень, если отдаёте SPA с бэка
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**").permitAll()
                        // всё остальное — ТОЛЬКО после входа
                        .anyRequest().authenticated()
                )
                // корректные ответы для неавторизованных/без прав
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) -> res.sendError(HttpServletResponse.SC_FORBIDDEN))
                )
                // 1) JWT — ДО UsernamePasswordAuthenticationFilter
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

    // Если фронт на другом домене/порту — включите CORS с credential'ами
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowCredentials(true);
        // Укажите точные origin'ы фронта:
        cfg.setAllowedOriginPatterns(List.of(
                "https://warehouse-qr-app-8adwv.ondigitalocean.app",
                "http://localhost:*"
        ));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
