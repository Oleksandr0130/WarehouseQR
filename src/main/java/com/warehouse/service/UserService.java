package com.warehouse.service;

import com.warehouse.model.Company;
import com.warehouse.model.User;
import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.repository.CompanyRepository;
import com.warehouse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Сервис регистрации и подтверждения пользователя.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CompanyService companyService; // Используем сервис вместо репозитория
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ConfirmationCodeService codeService;

    public User registerUser(UserRegistrationDTO registrationDTO) {
        // Проверяем, есть ли пользователь с указанным email
        if (userRepository.findByEmail(registrationDTO.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким email уже зарегистрирован");
        }

        // Используем метод CompanyService для нахождения или создания компании
        Company company = companyService.registerOrFindCompany(registrationDTO.getCompanyName());

        // Создаём нового пользователя
        User user = new User();
        user.setUsername(registrationDTO.getUsername());
        user.setEmail(registrationDTO.getEmail());
        user.setPassword(passwordEncoder.encode(registrationDTO.getPassword()));
        user.setRole(registrationDTO.getRole());
        user.setEnabled(false);
        user.setCompany(company); // Привязываем компанию к пользователю

        // Сохраняем пользователя в базе
        User savedUser = userRepository.save(user);

        // Генерируем код подтверждения
        String code = codeService.generateConfirmationCode(savedUser);

        // Формируем ссылку подтверждения
        String confirmationLink = "https://warehouse-qr-app-8adwv.ondigitalocean.app/api/confirmation?code=" + code;

        // Отправляем email с ссылкой подтверждения
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

    public User getCurrentUser() {
        String username = ((UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

}
