package com.warehouse.repository;

import com.warehouse.model.Company;
import com.warehouse.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByConfirmationCode(String confirmationCode);

    boolean existsByUsername(String username); // Проверяет наличие пользователя по имени
    boolean existsByEmail(String email);      // Проверяет наличие пользователя по email

    List<User> findByCompany(Company company); // Список пользователей определенной компании

    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.username = :username")
    Optional<User> findByUsernameWithCompany(@Param("username") String username);


    boolean existsByEmailAndCompany(String email, Company company); // Проверка email в рамках компании
}