package com.warehouse.utils;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Доступ к папке "qrcodes" из корня
        registry.addResourceHandler("/qrcodes/**")
                .addResourceLocations("file:./qrcodes/"); // Указывает, что папка лежит в корне проекта

        // Доступ к папке "резервации" из корня
        registry.addResourceHandler("/reservation/**")
                .addResourceLocations("file:./reservation/");
    }

}