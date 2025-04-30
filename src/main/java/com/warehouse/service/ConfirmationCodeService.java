package com.warehouse.service;

import com.warehouse.model.User;
import com.warehouse.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Сервис для генерации уникальных кодов подтверждения и их хранения у пользователя.
 */
@Service
public class ConfirmationCodeService {

    private final UserRepository userRepository;

    public ConfirmationCodeService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String generateConfirmationCode(User user) {
        String code = UUID.randomUUID().toString();
        user.setConfirmationCode(code);
        userRepository.save(user);
        return code;
    }
}