package com.warehouse.controller;

import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.service.CompanyService;
import com.warehouse.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final CompanyService companyService; // Сервис компании

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody UserRegistrationDTO registrationDTO) {
        try {
            // Привязываем пользователя к компании (по названию)
            userService.registerUser(registrationDTO);
            return ResponseEntity.ok("Пользователь успешно зарегистрирован");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
