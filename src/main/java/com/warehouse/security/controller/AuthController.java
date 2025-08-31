package com.warehouse.security.controller;

import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.repository.UserRepository;
import com.warehouse.security.dto.LoginRequest;
import com.warehouse.security.service.JwtTokenProvider;
import com.warehouse.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

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

// AuthController.java (полные методы)

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(u -> u.isEnabled() && passwordEncoder.matches(request.getPassword(), u.getPassword()))
                .map(u -> {
                    String access = jwtTokenProvider.generateAccessToken(u.getUsername());
                    String refresh = jwtTokenProvider.generateRefreshToken(u.getUsername());

                    ResponseCookie accessC = buildCookie("AccessToken", access, 30 * 60);
                    ResponseCookie refreshC = buildCookie("RefreshToken", refresh, 7 * 24 * 60 * 60);

                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, accessC.toString())
                            .header(HttpHeaders.SET_COOKIE, refreshC.toString())
                            .header(HttpHeaders.CACHE_CONTROL, "no-store")
                            .<Object>build();
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Object>build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Object> refreshToken(HttpServletRequest request) {
        String refresh = getCookie(request, "RefreshToken");
        if (refresh == null || !jwtTokenProvider.validateToken(refresh)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Object>build();
        }
        String username = jwtTokenProvider.getUsername(refresh);

        ResponseCookie accessC = buildCookie("AccessToken", jwtTokenProvider.generateAccessToken(username), 30 * 60);
        ResponseCookie refreshC = buildCookie("RefreshToken", jwtTokenProvider.generateRefreshToken(username), 7 * 24 * 60 * 60);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessC.toString())
                .header(HttpHeaders.SET_COOKIE, refreshC.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .<Object>build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Object> logout() {
        ResponseCookie accessC = deleteCookie("AccessToken");
        ResponseCookie refreshC = deleteCookie("RefreshToken");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessC.toString())
                .header(HttpHeaders.SET_COOKIE, refreshC.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .<Object>build();
    }

    private ResponseCookie buildCookie(String name, String value, int maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)                           // прод — только HTTPS
                .path("/")                              // покрывает /api/*
                .sameSite("Lax")                        // фронт и API на одном origin
                .domain("warehouse-qr-app-8adwv.ondigitalocean.app") // ЯВНО укажем host
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie deleteCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Lax")
                .domain("warehouse-qr-app-8adwv.ondigitalocean.app")
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
