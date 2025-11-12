package com.warehouse.controller;

import com.warehouse.model.User;
import com.warehouse.model.dto.AdminCreateUserRequest;
import com.warehouse.model.dto.UserDTO;
import com.warehouse.service.UserService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class UserController {

    private final UserService userService;

    /** Профиль текущего пользователя */
    @GetMapping("/users/me")
    public ResponseEntity<UserDTO> me() {
        User user = userService.getCurrentUser(); // уже есть в сервисе
        UserDTO dto = new UserDTO(
                user.getId(), // 👈 нужен фронту
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
                created.getId(), // 👈 нужен фронту
                created.getUsername(),
                created.getEmail(),
                created.getCompany() != null ? created.getCompany().getName() : null,
                "ROLE_ADMIN".equalsIgnoreCase(created.getRole())
        );
        return ResponseEntity.ok(dto);
    }

    /** Список пользователей своей компании (для таблицы на странице аккаунта) — только админ */
    @GetMapping("/admin/users")
    public ResponseEntity<List<UserDTO>> listMyCompanyUsers() {
        return ResponseEntity.ok(userService.listMyCompanyUsers());
    }

    /** Смена роли участника своей компании — только админ */
    @PutMapping("/admin/users/{id}/role")
    public ResponseEntity<Void> updateRole(@PathVariable("id") Long userId,
                                           @RequestBody UpdateRoleRequest body) {
        userService.updateMemberRole(userId, body.isAdmin());
        return ResponseEntity.noContent().build();
    }

    /** Удаление участника своей компании — только админ */
    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable("id") Long userId) {
        userService.deleteMember(userId);
        return ResponseEntity.noContent().build();
    }

    /** Удалить свой аккаунт */
    @DeleteMapping("/users/me") // 👈 ведущий слэш обязателен
    public ResponseEntity<Void> deleteMyAccount(Authentication authentication) {
        String username = authentication.getName();
        userService.deleteUserAndRelatedData(username);
        return ResponseEntity.noContent().build();
    }

    /** Тело запроса для смены роли (простое и без отдельного файла) */
    @Data
    public static class UpdateRoleRequest {
        private boolean admin;
    }
}
