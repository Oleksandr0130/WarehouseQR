package com.warehouse.service;

import com.warehouse.model.Company;
import com.warehouse.model.User;
import com.warehouse.model.dto.UserDTO;
import com.warehouse.model.dto.UserRegistrationDTO;
import com.warehouse.repository.CompanyRepository;
import com.warehouse.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис регистрации и подтверждения пользователя + операции команды для фронта.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final CompanyRepository companyRepository;
    private final CompanyService companyService; // Используем сервис вместо репозитория
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ConfirmationCodeService codeService;

    /* ===================== Регистрация / подтверждение (твоя логика — без изменений) ===================== */

    public User registerUser(UserRegistrationDTO registrationDTO) {
        // ✅ Оставляем как было (чтобы ничего не ломать)
        // По умолчанию будет ru (в EmailService старый метод остаётся)
        // Но если AuthController передал язык — будет использоваться перегруженный метод ниже
        return registerUser(registrationDTO, null);
    }

    // ✅ Новый метод: принимает Accept-Language и выбирает язык письма
    public User registerUser(UserRegistrationDTO registrationDTO, String acceptLanguage) {
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
        user.setRole("ROLE_ADMIN");
        user.setEnabled(false);
        companyService.startTrial(company, 30); // внутри save
        company.setEnabled(true);
        user.setCompany(company); // Привязываем компанию к пользователю

        // Сохраняем пользователя в базе
        User savedUser = userRepository.save(user);

        // Генерируем код подтверждения
        String code = codeService.generateConfirmationCode(savedUser);

        // ✅ Определяем язык из Accept-Language
        String lang = normalizeLang(acceptLanguage);

        // ✅ Формируем ссылку подтверждения (+lang)
        String confirmationLink =
                "https://warehouse-qr-app-8adwv.ondigitalocean.app/api/confirmation?code=" + code
                        + "&lang=" + lang;

        // ✅ Отправляем email с ссылкой подтверждения (новый метод в EmailService)
        emailService.sendConfirmationEmail(savedUser.getEmail(), savedUser.getUsername(), confirmationLink, lang);

        return savedUser;
    }

    // ✅ Минимальный парсер Accept-Language (не влияет на остальную логику)
    private String normalizeLang(String acceptLanguage) {
        // чтобы поведение по умолчанию осталось как раньше (RU)
        if (acceptLanguage == null || acceptLanguage.isBlank()) return "ru";

        String l = acceptLanguage.toLowerCase();

        // Примеры:
        // "de-DE,de;q=0.9,en;q=0.8"
        // "pl"
        // "ru-RU"
        if (l.startsWith("de")) return "de";
        if (l.startsWith("pl")) return "pl";
        if (l.startsWith("en")) return "en";
        if (l.startsWith("ru")) return "ru";

        return "en";
    }

    public void confirmEmail(String confirmationCode) {
        User user = userRepository.findByConfirmationCode(confirmationCode)
                .orElseThrow(() -> new IllegalArgumentException("Некорректный код подтверждения"));
        user.setEnabled(true);
        user.setConfirmationCode(null);
        userRepository.save(user);
    }

    public User getCurrentUser() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            // Проверка на анонимного пользователя
            if (principal.equals("anonymousUser") ||
                    SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken) {
                throw new IllegalStateException("Пожалуйста, выполните вход.");
            }

            // Если principal - это строка (имя пользователя)
            if (principal instanceof String username) {
                System.out.println("Имя текущего пользователя: " + username);

                return userRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("Пользователь с именем '" + username + "' не найден."));
            }

            // Если principal - это UserDetails
            if (principal instanceof UserDetails userDetails) {
                System.out.println("Детали текущего пользователя: " + userDetails.getUsername());

                return userRepository.findByUsername(userDetails.getUsername())
                        .orElseThrow(() -> new UsernameNotFoundException("Пользователь с именем '" + userDetails.getUsername() + "' не найден."));
            }

            throw new IllegalStateException("Принципал содержит недопустимый объект: " + principal.getClass());
        } catch (Exception e) {
            System.err.println("Ошибка получения текущего пользователя: " + e.getMessage());
            throw new RuntimeException("Не удалось определить текущего пользователя.", e);
        }
    }

    /**
     * Создать пользователя в своей компании (только для ROLE_ADMIN).
     * Новому пользователю автоматически ставится роль ROLE_USER.
     */
    @Transactional
    public User createUserByAdmin(String username, String email, String rawPassword) {
        User current = getCurrentUser();

        if (!"ROLE_ADMIN".equalsIgnoreCase(current.getRole())) {
            throw new IllegalStateException("Недостаточно прав. Требуется ROLE_ADMIN.");
        }

        // проверки уникальности в рамках компании (или глобально)
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
        u.setRole("ROLE_USER");                    // фиксированная роль
        u.setEnabled(true);                        // админ создал — сразу активен
        u.setCompany(current.getCompany());        // та же компания, что у админа

        return userRepository.save(u);
    }

    /**
     * ✅ FIX: правильный порядок удаления
     * - если админ: удалить всех пользователей компании -> удалить компанию
     * - иначе: удалить только пользователя
     */
    @Transactional
    public void deleteUserAndRelatedData(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Если админ — удалить компанию и всех связанных пользователей
        if ("ROLE_ADMIN".equals(user.getRole()) && user.getCompany() != null) {
            Long companyId = user.getCompany().getId();

            // 1) удалить всех пользователей компании (включая админа)
            List<User> companyUsers = userRepository.findAllByCompanyId(companyId);
            userRepository.deleteAll(companyUsers);

            // 2) удалить компанию
            companyRepository.deleteById(companyId);
            return;
        }

        // Обычный пользователь — удаляем только его
        userRepository.delete(user);
    }
    /* ===================== Методы для фронта (список, роль, удаление, профиль) ===================== */

    /** Профиль текущего пользователя в виде DTO (для /users/me). */
    @Transactional
    public UserDTO getMeDto() {
        User me = getCurrentUser();
        return toDto(me);
    }

    /** Список пользователей своей компании (для /admin/users). */
    @Transactional
    public List<UserDTO> listMyCompanyUsers() {
        User me = getCurrentUser();
        if (!"ROLE_ADMIN".equalsIgnoreCase(me.getRole())) {
            throw new IllegalStateException("Недостаточно прав. Требуется ROLE_ADMIN.");
        }
        return userRepository.findAllByCompanyId(me.getCompany().getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    /** Обновить роль участника своей компании (для PUT /admin/users/{id}/role). */
    @Transactional
    public void updateMemberRole(Long memberId, boolean admin) {
        User me = getCurrentUser();
        if (!"ROLE_ADMIN".equalsIgnoreCase(me.getRole())) {
            throw new IllegalStateException("Недостаточно прав. Требуется ROLE_ADMIN.");
        }

        // Нельзя менять свою роль
        if (me.getId().equals(memberId)) {
            throw new IllegalArgumentException("Нельзя менять собственную роль.");
        }

        // Пользователь должен принадлежать той же компании
        User target = userRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        if (!target.getCompany().getId().equals(me.getCompany().getId())) {
            throw new IllegalStateException("Недостаточно прав для изменения пользователя из другой компании.");
        }

        target.setRole(admin ? "ROLE_ADMIN" : "ROLE_USER");
        userRepository.save(target);
    }

    /** Удалить участника своей компании (для DELETE /admin/users/{id}). */
    @Transactional
    public void deleteMember(Long memberId) {
        User me = getCurrentUser();
        if (!"ROLE_ADMIN".equalsIgnoreCase(me.getRole())) {
            throw new IllegalStateException("Недостаточно прав. Требуется ROLE_ADMIN.");
        }

        // Нельзя удалить себя
        if (me.getId().equals(memberId)) {
            throw new IllegalArgumentException("Нельзя удалить самого себя.");
        }

        // Проверка принадлежности компании
        boolean allowed = userRepository.existsByIdAndCompanyId(memberId, me.getCompany().getId());
        if (!allowed) {
            throw new IllegalStateException("Недостаточно прав для удаления пользователя из другой компании.");
        }

        userRepository.deleteById(memberId);
    }

    /* ===================== Маппер ===================== */

    private UserDTO toDto(User u) {
        String companyName = u.getCompany() != null ? u.getCompany().getName() : null;
        boolean admin = "ROLE_ADMIN".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole());
        UserDTO dto = new UserDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setEmail(u.getEmail());
        dto.setCompanyName(companyName);
        dto.setAdmin(admin);
        return dto;
    }
}
