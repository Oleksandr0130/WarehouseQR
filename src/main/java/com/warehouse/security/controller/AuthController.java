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

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> user.isEnabled() && passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .map(user -> {
                    String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
                    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

                    ResponseCookie access = buildCookie("AccessToken", accessToken, 30 * 60);           // 30 мин
                    ResponseCookie refresh = buildCookie("RefreshToken", refreshToken, 7 * 24 * 60 * 60); // 7 дней

                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, access.toString(), refresh.toString())
                            .header(HttpHeaders.CACHE_CONTROL, "no-store")
                            .<Object>build();
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Object>build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Object> refreshToken(HttpServletRequest request) {
        String refreshToken = getCookie(request, "RefreshToken");
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Object>build();
        }
        String username = jwtTokenProvider.getUsername(refreshToken);

        String newAccess = jwtTokenProvider.generateAccessToken(username);
        String newRefresh = jwtTokenProvider.generateRefreshToken(username);

        ResponseCookie access = buildCookie("AccessToken", newAccess, 30 * 60);
        ResponseCookie refresh = buildCookie("RefreshToken", newRefresh, 7 * 24 * 60 * 60);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, access.toString(), refresh.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .<Object>build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Object> logout() {
        ResponseCookie access = deleteCookie("AccessToken");
        ResponseCookie refresh = deleteCookie("RefreshToken");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, access.toString(), refresh.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .<Object>build();
    }

    /* ================= helpers ================= */

    private ResponseCookie buildCookie(String name, String value, int maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)                 // на проде обязательно HTTPS
                .path("/")                    // покрывает /api тоже
                .sameSite("None")             // фронт и API могут быть разными origin
                // .domain("warehouse-qr-app-8adwv.ondigitalocean.app") // можно явно указать при необходимости
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
