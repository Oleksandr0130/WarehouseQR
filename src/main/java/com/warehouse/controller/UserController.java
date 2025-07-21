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

        LocalDate today = LocalDate.now();

        if (user.isPaid()) {
            response.put("message", "Подписка активна.");
            response.put("status", "active");
        } else if (user.getTrialEndDate() != null && today.isBefore(user.getTrialEndDate())) {
            response.put("message", "Пробный период активен.");
            response.put("status", "trial");
        } else {
            response.put("message", "Пробный период истёк или отсутствует.");
            response.put("status", "expired");
        }

        return ResponseEntity.ok(response);
    }


}
