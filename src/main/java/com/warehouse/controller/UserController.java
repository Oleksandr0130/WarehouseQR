package com.warehouse.controller;

import com.warehouse.model.User;
import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.CompanyService;
import com.warehouse.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class UserController {
    private final CompanyService companyService; // Сервис компании
    private final UserRepository userRepository;

    @GetMapping("/{id}/subscription-status")
    public ResponseEntity<?> getSubscriptionStatus(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        Map<String, Object> response = new HashMap<>();
        response.put("isPaid", user.isPaid());
        response.put("trialStartDate", user.getTrialStartDate());
        response.put("trialEndDate", user.getTrialEndDate());

        // Проверяем, истек ли пробный период
        LocalDate today = LocalDate.now();
        if (!user.isPaid() && (user.getTrialEndDate() == null || today.isAfter(user.getTrialEndDate()))) {
            response.put("message", "Ваш пробный период истёк. Подпишитесь, чтобы продолжить пользоваться системой.");
            response.put("status", "expired");
            return ResponseEntity.status(403).body(response); // Отправляем статус 403
        }

        response.put("message", user.isPaid() ? "Подписка оплачена." : "Пробный период активен.");
        response.put("status", user.isPaid() ? "active" : "trial");
        return ResponseEntity.ok(response);
    }


}
