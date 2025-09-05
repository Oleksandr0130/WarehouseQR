package com.warehouse.security.controller;

import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.security.dto.JwtResponse;
import com.warehouse.security.dto.LoginRequest;
import com.warehouse.security.service.JwtTokenProvider;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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
        // Проверка уникальности пользователя по username
        if (userRepository.existsByUsername(registrationDTO.getUsername())) {
            return ResponseEntity.badRequest().body("Пользователь с таким именем уже существует.");
        }

        // Проверка уникальности пользователя по email
        if (userRepository.existsByEmail(registrationDTO.getEmail())) {
            return ResponseEntity.badRequest().body("Пользователь с таким email уже существует.");
        }

        // Передаём логику регистрации (включая привязку компании) в UserService
        userService.registerUser(registrationDTO);

        return ResponseEntity.ok("Регистрация завершена. Проверьте email для активации учётной записи.");
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> user.isEnabled() &&
                        passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .map(user -> {
                    String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
                    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

                    // Устанавливаем токены в cookies
                    addCookie(response, "AccessToken", accessToken, 60 * 60); // 30 минут
                    addCookie(response, "RefreshToken", refreshToken, 7 * 24 * 60 * 60); // 7 дней

                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Object> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getCookieValue(request, "RefreshToken");
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = jwtTokenProvider.getUsername(refreshToken);

        String newAccessToken = jwtTokenProvider.generateAccessToken(username);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

        addCookie(response, "AccessToken", newAccessToken, 60 * 60);
        addCookie(response, "RefreshToken", newRefreshToken, 7 * 24 * 60 * 60);

        // если фронту что-то нужно в ответе — можно вернуть минимальный JSON
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private String getCookieValue(HttpServletRequest request, String cookieName) {
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }


    private void addCookie(HttpServletResponse response, String name, String token, int maxAgeInSeconds) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(name, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeInSeconds);
        // Если фронт и API на разных доменах:
        cookie.setAttribute("SameSite", "None");
        // Если на одном домене, лучше:
        // cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
