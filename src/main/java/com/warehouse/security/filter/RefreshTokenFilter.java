// src/main/java/com/warehouse/security/filter/RefreshTokenFilter.java
package com.warehouse.security.filter;

import com.warehouse.security.service.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RefreshTokenFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public RefreshTokenFilter(JwtTokenProvider jwtTokenProvider,
                              UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String access = getCookieValue(request, "AccessToken");
        boolean accessOk = access != null && jwtTokenProvider.validateToken(access);

        if (!accessOk && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Пытаемся «тихо» обновить по refresh
            String refresh = getCookieValue(request, "RefreshToken");
            if (refresh != null && jwtTokenProvider.validateToken(refresh)) {
                String username = jwtTokenProvider.getUsername(refresh);

                // Выпускаем новые токены
                String newAccess = jwtTokenProvider.generateAccessToken(username);
                String newRefresh = jwtTokenProvider.generateRefreshToken(username);
                addCookie(response, "AccessToken", newAccess, 60 * 60);
                addCookie(response, "RefreshToken", newRefresh, 30 * 24 * 60 * 60);

                // Сразу аутентифицируем пользователя в текущем запросе
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                var auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static void addCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        // если фронт на другом домене:
        cookie.setAttribute("SameSite", "None");
        response.addCookie(cookie);
    }
}
