package com.warehouse.controller;

import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//@RestController
//@RequestMapping("/folders")
//public class FolderController {
//
//    @GetMapping("/qrcodes")
//    public List<String> listQRCodes() {
//        File folder = new File("./qrcodes");
//        if (folder.exists() && folder.isDirectory()) {
//            File[] files = folder.listFiles();
//            if (files != null) {
//                return Stream.of(files)
//                        .filter(file -> !file.isDirectory())
//                        .map(file -> "/qrcodes/" + file.getName())
//                        .collect(Collectors.toList());
//            }
//        }
//        return List.of();
//    }
//
//    @GetMapping("/reservation")
//    public List<String> listReservations() {
//        File folder = new File("./reservation");
//        if (folder.exists() && folder.isDirectory()) {
//            File[] files = folder.listFiles();
//            if (files != null) {
//                return Stream.of(files)
//                        .filter(file -> !file.isDirectory())
//                        .map(file -> "/reservation/" + file.getName())
//                        .collect(Collectors.toList());
//            }
//        }
//        return List.of();
//    }

@RestController
@RequestMapping("/folders")
public class FolderController {

    // Использование значений из application.properties/application.yml
    @Value("${file-storage.qrcodes:./qrcodes}")
    private String qrCodesPath;

    @Value("${file-storage.reservation:./reservation}")
    private String reservationPath;

    // Логгер для отслеживания работы API
    private static final Logger logger = LoggerFactory.getLogger(FolderController.class);

    // Оригинальный метод для получения QR-кодов
    @GetMapping("/qrcodes")
    public List<String> listQRCodes() {
        logger.info("Получение списка QR-кодов...");
        return listFiles(new File(qrCodesPath), "/qrcodes/");
    }

    // Оригинальный метод для получения списка резервных файлов
    @GetMapping("/reservation")
    public List<String> listReservations() {
        logger.info("Получение списка резервных файлов...");
        return listFiles(new File(reservationPath), "/reservation/");
    }

    // Новый метод для работы с папками
    private List<String> listFiles(File folder, String urlPrefix) {
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                return Stream.of(files)
                        .filter(file -> !file.isDirectory())
                        .map(file -> urlPrefix + file.getName())
                        .collect(Collectors.toList());
            }
        }
        logger.warn("Папка {} отсутствует или пуста.", folder.getAbsolutePath());
        return List.of();
    }

    // Пост-инициализация директорий, если они отсутствуют
    @PostConstruct
    public void initDirectories() {
        logger.info("Проверка инициализации директорий...");
        createDirectoryIfNotExists(qrCodesPath);
        createDirectoryIfNotExists(reservationPath);
    }

    private void createDirectoryIfNotExists(String path) {
        File directory = new File(path);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                logger.info("Директория создана по пути: {}", path);
            } else {
                logger.error("Не удалось создать директорию по пути: {}", path);
            }
        } else {
            logger.info("Директория уже существует: {}", path);
        }
    }
}

// Конфигурация для предоставления статических файлов через /qrcodes и /reservation
@Configuration
class WebConfig implements WebMvcConfigurer {

    @Value("${file-storage.qrcodes:./qrcodes}")
    private String qrCodesPath;

    @Value("${file-storage.reservation:./reservation}")
    private String reservationPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/qrcodes/**")
                .addResourceLocations("file:" + qrCodesPath + "/");
        registry.addResourceHandler("/reservation/**")
                .addResourceLocations("file:" + reservationPath + "/");
    }
}
