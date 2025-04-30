package com.warehouse.controller;

import com.warehouse.service.UserService;
import com.warehouse.exeption_handling.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Контроллер для обработки подтверждения по коду.
 */
@RestController
@RequestMapping("/confirmation")
public class ConfirmController {

    private final UserService userService;

    public ConfirmController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Response confirmEmail(@RequestParam String code) {
        userService.confirmEmail(code);
        return new Response("Почта успешно подтверждена");
    }
}