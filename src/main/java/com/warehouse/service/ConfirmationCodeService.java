package com.warehouse.service;

import com.warehouse.model.User;
import com.warehouse.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Сервис для генерации уникальных кодов подтверждения и их хранения у пользователя.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmationCodeService {

    private final UserRepository userRepository;

    // генерация кода (сохраняем прямо в User)
    public String generateConfirmationCode(User user) {
        String code = UUID.randomUUID().toString();
        user.setConfirmationCode(code);
        user.setConfirmationExpiry(Instant.now().plus(1, ChronoUnit.DAYS));
        userRepository.save(user);
        return code;
    }

    // подтверждение кода
    @Transactional
    public boolean confirmCode(String code) {
        try {
            User user = userRepository.findByConfirmationCode(code)
                    .orElse(null);

            if (user == null) {
                return false;
            }

            // проверка срока действия
            if (user.getConfirmationExpiry() != null &&
                    user.getConfirmationExpiry().isBefore(Instant.now())) {
                return false;
            }

            // активируем пользователя
            user.setEnabled(true);
            user.setConfirmationCode(null);   // удаляем код
            user.setConfirmationExpiry(null); // очищаем срок
            userRepository.save(user);

            return true;
        } catch (Exception e) {
            log.error("Ошибка при подтверждении кода {}: {}", code, e.getMessage());
            return false;
        }
    }
}