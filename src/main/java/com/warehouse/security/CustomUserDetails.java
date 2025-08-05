package com.warehouse.security;

import com.warehouse.model.Company;
import com.warehouse.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@Getter
public class CustomUserDetails implements UserDetails {

    private final User user; // Внутренний пользователь

    public CustomUserDetails(User user) {
        this.user = user; // Вы передаёте ваш объект User в этот класс
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return org.springframework.security.core.authority.AuthorityUtils.createAuthorityList(user.getRole());
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Можно настроить логику истечения аккаунта
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    public Company getCompany() {
        return user.getCompany(); // Теперь можно получить связанные компании из вашего объекта User
    }
}

