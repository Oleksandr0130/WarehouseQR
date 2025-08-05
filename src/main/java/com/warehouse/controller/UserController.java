package com.warehouse.controller;

import com.warehouse.model.User;
import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.service.CompanyService;
import com.warehouse.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final CompanyService companyService; // Сервис компании

    private final UserService userService;

    /**
     * Получение информации о текущем пользователе и компании.
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser() {
        try {
            User currentUser = userService.getCurrentUser();
            return ResponseEntity.ok(Map.of(
                    "username", currentUser.getUsername(),
                    "companyName", currentUser.getCompany().getName()
            ));
        } catch (Exception e) {
            log.error("Ошибка получения текущего пользователя: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Ошибка получения текущего пользователя.");
        }
    }

}
