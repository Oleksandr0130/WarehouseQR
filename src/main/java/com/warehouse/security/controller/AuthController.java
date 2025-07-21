// AuthController.java
package com.warehouse.security.controller;

import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.security.dto.JwtResponse;
import com.warehouse.security.dto.LoginRequest;
import com.warehouse.security.service.JwtTokenProvider;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
    public ResponseEntity<Map<String, String>> register(@RequestBody UserRegistrationDTO registrationDTO) {
        if (userRepository.existsByUsername(registrationDTO.getUsername())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Пользователь с таким именем уже существует.");
            return ResponseEntity.badRequest().body(response);
        }

        if (userRepository.existsByEmail(registrationDTO.getEmail())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Пользователь с таким email уже существует.");
            return ResponseEntity.badRequest().body(response);
        }

        userService.registerUser(registrationDTO);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Регистрация завершена. Проверьте email для активации учётной записи.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> user.isEnabled() &&
                        passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .map(user -> {
                    String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
                    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

                    addCookie(response, "AccessToken", accessToken, 30 * 60);
                    addCookie(response, "RefreshToken", refreshToken, 7 * 24 * 60 * 60);

                    Map<String, String> responseBody = new HashMap<>();
                    responseBody.put("userId", user.getId().toString());
                    responseBody.put("accessToken", accessToken);
                    responseBody.put("refreshToken", refreshToken);
                    return ResponseEntity.ok(responseBody);
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Неверные учетные данные")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshToken(@RequestBody Map<String, String> request, HttpServletResponse response) {
        String refreshToken = request.get("refreshToken");

        if (jwtTokenProvider.validateToken(refreshToken)) {
            String username = jwtTokenProvider.getUsername(refreshToken);
            String newAccessToken = jwtTokenProvider.generateAccessToken(username);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

            addCookie(response, "AccessToken", newAccessToken, 30 * 60);
            addCookie(response, "RefreshToken", newRefreshToken, 7 * 24 * 60 * 60);

            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("accessToken", newAccessToken);
            responseBody.put("refreshToken", newRefreshToken);
            return ResponseEntity.ok(responseBody);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Недействительный или истёкший refresh token"));
        }
    }

    private void addCookie(HttpServletResponse response, String name, String token, int maxAgeInSeconds) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(name, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeInSeconds);
        response.addCookie(cookie);
    }
}