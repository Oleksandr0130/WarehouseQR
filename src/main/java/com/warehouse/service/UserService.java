package com.warehouse.service;

import com.warehouse.model.Company;
import com.warehouse.model.User;
import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ConfirmationCodeService codeService;

    public User registerUser(UserRegistrationDTO registrationDTO) {
        if (userRepository.findByEmail(registrationDTO.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким email уже зарегистрирован");
        }

        Company company = companyService.registerOrFindCompany(registrationDTO.getCompanyName());

        User user = new User();
        user.setUsername(registrationDTO.getUsername());
        user.setEmail(registrationDTO.getEmail());
        user.setPassword(passwordEncoder.encode(registrationDTO.getPassword()));
        user.setRole("ROLE_ADMIN");
        user.setEnabled(false);
        companyService.startTrial(company, 5);
        company.setEnabled(true);
        user.setCompany(company);

        User savedUser = userRepository.save(user);

        String code = codeService.generateConfirmationCode(savedUser);
        String confirmationLink = "https://warehouse-qr-app-8adwv.ondigitalocean.app/api/confirmation?code=" + code;
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Пожалуйста, выполните вход.");
        }

        String username;
        Object principal = auth.getPrincipal();

        if (principal instanceof UserDetails ud) {
            username = ud.getUsername();
        } else if (principal instanceof String s) {
            if ("anonymousUser".equals(s)) {
                throw new AccessDeniedException("Пожалуйста, выполните вход.");
            }
            username = s;
        } else {
            throw new AccessDeniedException("Не удалось определить текущего пользователя.");
        }

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Пользователь не найден или неактивен."));
    }

    @Transactional
    public User createUserByAdmin(String username, String email, String rawPassword) {
        User current = getCurrentUser();

        if (!"ROLE_ADMIN".equalsIgnoreCase(current.getRole())) {
            throw new AccessDeniedException("Недостаточно прав. Требуется ROLE_ADMIN.");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Пользователь с таким именем уже существует");
        }

        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRole("ROLE_USER");
        u.setEnabled(true);
        u.setCompany(current.getCompany());

        return userRepository.save(u);
    }
}
