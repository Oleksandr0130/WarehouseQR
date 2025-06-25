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


//    @PostMapping("/login")
//    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest request) {
//        return userRepository.findByUsername(request.getUsername())
//                .filter(user -> user.isEnabled() &&
//                        passwordEncoder.matches(request.getPassword(), user.getPassword()))
//                .map(user -> {
//                    String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
//                    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());
//                    return ResponseEntity.ok(new JwtResponse(accessToken, refreshToken));
//                })
//                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
//    }
//
//    @PostMapping("/refresh")
//    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
//        String refreshToken = request.get("refreshToken");
//
//        if (jwtTokenProvider.validateToken(refreshToken)) {
//            String username = jwtTokenProvider.getUsername(refreshToken);
//
//            // Генерация новой пары токенов
//            String newAccessToken = jwtTokenProvider.generateAccessToken(username);
//            String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);
//
//            return ResponseEntity.ok(Map.of(
//                    "accessToken", newAccessToken,
//                    "refreshToken", newRefreshToken
//            ));
//        } else {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Недействительный или истёкший refresh token");
//        }
//    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> user.isEnabled() &&
                        passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .map(user -> {
                    String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
                    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

                    // Устанавливаем токены в cookies
                    addCookie(response, "AccessToken", accessToken, 30 * 60); // 30 минут
                    addCookie(response, "RefreshToken", refreshToken, 7 * 24 * 60 * 60); // 7 дней

                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Object> refreshToken(@RequestBody Map<String, String> request, HttpServletResponse response) {
        String refreshToken = request.get("refreshToken");

        if (jwtTokenProvider.validateToken(refreshToken)) {
            String username = jwtTokenProvider.getUsername(refreshToken);

            // Генерация новой пары токенов
            String newAccessToken = jwtTokenProvider.generateAccessToken(username);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

            // Устанавливаем токены в cookies
            addCookie(response, "AccessToken", newAccessToken, 30 * 60);
            addCookie(response, "RefreshToken", newRefreshToken, 7 * 24 * 60 * 60);

            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

    private void addCookie(HttpServletResponse response, String name, String token, int maxAgeInSeconds) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(name, token);
        cookie.setHttpOnly(true); // Доступ только через HTTP
        cookie.setSecure(true);   // Только для HTTPS
        cookie.setPath("/");      // Доступен для всего приложения
        cookie.setMaxAge(maxAgeInSeconds); // Время жизни в секундах
        response.addCookie(cookie);
    }
}
