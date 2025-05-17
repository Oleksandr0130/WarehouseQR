package com.warehouse.security.controller;

import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.security.dto.JwtResponse;
import com.warehouse.security.dto.LoginRequest;
import com.warehouse.security.service.JwtTokenProvider;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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

//    @PostMapping("/register")
//    public ResponseEntity<String> register(@RequestBody UserRegistrationDTO registrationDTO) {
//        if (userRepository.existsByUsername(registrationDTO.getUsername())) {
//            return ResponseEntity.badRequest().body("Пользователь с таким именем уже существует.");
//        }
//
//        if (userRepository.existsByEmail(registrationDTO.getEmail())) {
//            return ResponseEntity.badRequest().body("Пользователь с таким email уже существует.");
//        }
//
//        userService.registerUser(registrationDTO);
//        return ResponseEntity.ok("Регистрация завершена. Проверьте email для активации учётной записи.");
//    }
//
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
//}

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
    public ResponseEntity<String> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> user.isEnabled() &&
                        passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .map(user -> {
                    // Генерация токенов
                    String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
                    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

                    // Запись токенов в HttpOnly-cookie
                    addHttpOnlyCookie(response, "accessToken", accessToken, 3600); // 1 час
                    addHttpOnlyCookie(response, "refreshToken", refreshToken, 30 * 24 * 3600); // 30 дней

                    return ResponseEntity.ok("Авторизация успешна");
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверные учетные данные или пользователь не активирован."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<String> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        // Достаем токен из cookie
        String refreshToken = extractTokenFromCookie(request, "refreshToken");

        if (refreshToken != null && jwtTokenProvider.validateToken(refreshToken)) {
            String username = jwtTokenProvider.getUsername(refreshToken);

            // Генерация новой пары токенов
            String newAccessToken = jwtTokenProvider.generateAccessToken(username);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

            // Обновление HttpOnly-cookie
            addHttpOnlyCookie(response, "accessToken", newAccessToken, 3600); // 1 час
            addHttpOnlyCookie(response, "refreshToken", newRefreshToken, 30 * 24 * 3600); // 30 дней

            return ResponseEntity.ok("Токены успешно обновлены");
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Недействительный или истёкший refreshToken");
    }

    // Добавление токенов в HttpOnly-cookie
    private void addHttpOnlyCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true); // Cookie недоступен через JavaScript
        cookie.setSecure(true); // Только HTTPS
        cookie.setPath("/"); // Доступен для всех URL приложения
        cookie.setMaxAge(maxAge); // Время жизни (в секундах)
        response.addCookie(cookie);
    }

    // Извлечение токена из cookie
    private String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals(cookieName)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
