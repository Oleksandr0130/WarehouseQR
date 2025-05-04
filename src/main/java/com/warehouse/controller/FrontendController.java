package com.warehouse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class FrontendController {
    // Перехватываем все запросы, кроме тех, что начинаются на /api
    @RequestMapping(value = { "/", "/**/{:[^.]*}" })
    public String forwardToFrontend() {
        // Возвращаем index.html из папки static
        return "forward:/index.html";
    }
}



