package com.warehouse.controller;

import com.warehouse.model.User;
import com.warehouse.model.dto.AdminCreateUserRequest;
import com.warehouse.model.dto.UserDTO;
import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.service.CompanyService;
import com.warehouse.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class UserController {
    private final UserService userService;

    /** Профиль текущего пользователя */
    @GetMapping("/users/me")
    public ResponseEntity<UserDTO> me() {
        User user = userService.getCurrentUser(); // уже есть в сервисе
        UserDTO dto = new UserDTO(
                user.getUsername(),
                user.getEmail(),
                user.getCompany() != null ? user.getCompany().getName() : null,
                "ROLE_ADMIN".equalsIgnoreCase(user.getRole())
        );
        return ResponseEntity.ok(dto);
    }

    /** Создание пользователя в своей компании (только админ) */
    @PostMapping("/admin/users")
    public ResponseEntity<UserDTO> createUserByAdmin(@Valid @RequestBody AdminCreateUserRequest req) {
        User created = userService.createUserByAdmin(req.getUsername(), req.getEmail(), req.getPassword());
        UserDTO dto = new UserDTO(
                created.getUsername(),
                created.getEmail(),
                created.getCompany() != null ? created.getCompany().getName() : null,
                "ROLE_ADMIN".equalsIgnoreCase(created.getRole())
        );
        return ResponseEntity.ok(dto);
    }
}
