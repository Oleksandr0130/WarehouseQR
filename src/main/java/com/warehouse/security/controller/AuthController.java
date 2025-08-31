package com.warehouse.security.controller;

import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.repository.UserRepository;
import com.warehouse.security.dto.LoginRequest;
import com.warehouse.security.service.JwtTokenProvider;
import com.warehouse.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserRegistrationDTO registrationDTO) {
        if (userRepository.existsByUsername(registrationDTO.getUsername())) {
            return ResponseEntity.badRequest().body("Пользователь с таким именем уже существует.");
        }
        if (userRepository.existsByEmail(registrationDTO.getEmail())) {
            return ResponseEntity.badRequest().body("Пользователь с таким email уже существует.");
        }
        userService.registerUser(registrationDTO);
        return ResponseEntity.ok("Регистрация завершена. Проверьте email для активации учётной записи.");
    }

    // ==== HYBRID: ставим куки + возвращаем JSON токены ====

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(u -> u.isEnabled() && passwordEncoder.matches(request.getPassword(), u.getPassword()))
                .map(u -> {
                    String access  = jwtTokenProvider.generateAccessToken(u.getUsername());
                    String refresh = jwtTokenProvider.generateRefreshToken(u.getUsername());

                    ResponseCookie accessC  = buildCookie("AccessToken",  access,  30 * 60);
                    ResponseCookie refreshC = buildCookie("RefreshToken", refresh, 7 * 24 * 60 * 60);

                    return ResponseEntity.ok()
                            .headers(h -> {
                                h.add(HttpHeaders.SET_COOKIE, accessC.toString());
                                h.add(HttpHeaders.SET_COOKIE, refreshC.toString());
                                h.add(HttpHeaders.CACHE_CONTROL, "no-store");
                                h.add("Pragma", "no-cache");
                            })
                            .body(Map.of("accessToken", access, "refreshToken", refresh));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    // Тело запроса refresh НЕобязательно; можно передать refreshToken в JSON или положиться на куку
    public static class RefreshRequest {
        public String refreshToken;
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshToken(
            @RequestBody(required = false) RefreshRequest body,
            HttpServletRequest request) {

        String refresh = (body != null && body.getRefreshToken() != null && !body.getRefreshToken().isBlank())
                ? body.getRefreshToken()
                : getCookie(request, "RefreshToken");

        if (refresh == null || !jwtTokenProvider.validateToken(refresh)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username   = jwtTokenProvider.getUsername(refresh);
        String newAccess  = jwtTokenProvider.generateAccessToken(username);
        String newRefresh = jwtTokenProvider.generateRefreshToken(username);

        ResponseCookie accessC  = buildCookie("AccessToken",  newAccess,  30 * 60);
        ResponseCookie refreshC = buildCookie("RefreshToken", newRefresh, 7 * 24 * 60 * 60);

        return ResponseEntity.ok()
                .headers(h -> {
                    h.add(HttpHeaders.SET_COOKIE, accessC.toString());
                    h.add(HttpHeaders.SET_COOKIE, refreshC.toString());
                    h.add(HttpHeaders.CACHE_CONTROL, "no-store");
                    h.add("Pragma", "no-cache");
                })
                .body(Map.of("accessToken", newAccess, "refreshToken", newRefresh));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie accessC  = deleteCookie("AccessToken");
        ResponseCookie refreshC = deleteCookie("RefreshToken");
        return ResponseEntity.ok()
                .headers(h -> {
                    h.add(HttpHeaders.SET_COOKIE, accessC.toString());
                    h.add(HttpHeaders.SET_COOKIE, refreshC.toString());
                    h.add(HttpHeaders.CACHE_CONTROL, "no-store");
                    h.add("Pragma", "no-cache");
                })
                .build();
    }

    // -------- helpers --------

    private ResponseCookie buildCookie(String name, String value, int maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)     // прод — только HTTPS
                .path("/")        // покрывает / и /api/**
                .sameSite("None") // безопасно при любых схемах и прокси
                // НЕ указываем .domain(...) — host-only cookie, меньше проблем
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie deleteCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(0)
                .build();
    }

    private String getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
