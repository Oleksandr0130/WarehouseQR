// ConfirmController.java
package com.warehouse.controller;

import com.warehouse.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/confirmation")
public class ConfirmController {

    private final UserService userService;

    @Value("${app.billing.frontend-base-url}") // например https://app.example.com
    private String frontendUrl;

    public ConfirmController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Void> confirmEmail(@RequestParam String code) {
        userService.confirmEmail(code); // бросит исключение, если код невалиден

        String target = String.format("%s/confirmed?status=success&code=%s",
                frontendUrl,
                UriUtils.encode(code, StandardCharsets.UTF_8));

        return ResponseEntity.status(HttpStatus.FOUND) // 302
                .location(URI.create(target))
                .build();
    }
}
