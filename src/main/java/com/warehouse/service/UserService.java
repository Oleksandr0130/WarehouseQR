package com.warehouse.service;

import com.warehouse.model.User;
import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Сервис регистрации и подтверждения пользователя.
 */
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ConfirmationCodeService codeService; // добавляем

    public User registerUser(UserRegistrationDTO registrationDTO) {
        if (userRepository.findByEmail(registrationDTO.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким email уже зарегистрирован");
        }

        User user = new User();
        user.setUsername(registrationDTO.getUsername());
        user.setEmail(registrationDTO.getEmail());
        user.setPassword(passwordEncoder.encode(registrationDTO.getPassword()));
        user.setRole(registrationDTO.getRole());
        user.setEnabled(false);

        User savedUser = userRepository.save(user);

        // Генерация уникального кода подтверждения
        String code = codeService.generateConfirmationCode(savedUser);

        // Создаем ссылку
//        String confirmationLink = "http://localhost:8080/api/confirmation?code=" + code;

        String confirmationLink = "https://warehouse-qr-app-8adwv.ondigitalocean.app/api/confirmation?code=" + code;

        // Отправляем письмо
        emailService.sendConfirmationEmail(savedUser.getEmail(), savedUser.getUsername(), confirmationLink);

        return savedUser;
    }

    public void confirmEmail(String confirmationCode) {
        User user = userRepository.findByConfirmationCode(confirmationCode)
                .orElseThrow(() -> new IllegalArgumentException("Некорректный код подтверждения"));
        user.setEnabled(true);
        user.setConfirmationCode(null);
        userRepository.save(user);
    }
}