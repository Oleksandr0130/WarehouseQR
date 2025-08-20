// ConfirmController.java
package com.warehouse.controller;

import com.warehouse.service.ConfirmationCodeService;
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

    private final ConfirmationCodeService confirmationCodeService;

    @Value("${app.billing.frontend-base-url}")
    private String frontendBaseUrl;

    public ConfirmController(ConfirmationCodeService confirmationCodeService) {
        this.confirmationCodeService = confirmationCodeService;
    }

    @GetMapping
    public ResponseEntity<Void> confirmEmail(@RequestParam("code") String code) {
        boolean confirmed;
        try {
            confirmed = confirmationCodeService.confirmCode(code);
        } catch (Exception e) {
            confirmed = false;
        }

        String redirectUrl = frontendBaseUrl + "/confirmed?status=" + (confirmed ? "success" : "error");
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }
}
