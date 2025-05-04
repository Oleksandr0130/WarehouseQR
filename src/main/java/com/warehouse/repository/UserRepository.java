package com.warehouse.repository;

import com.warehouse.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByConfirmationCode(String confirmationCode);

    boolean existsByUsername(String username); // Проверяет наличие пользователя по имени
    boolean existsByEmail(String email);      // Проверяет наличие пользователя по email
}