package com.warehouse.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class QRCodeGenerator {
//
//    public static void generateQRCode(String text, String filePath) throws IOException {
//        QRCodeWriter qrCodeWriter = new QRCodeWriter();
//        try {
//            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);
//
//            // Получаем путь для файла
//            Path path = FileSystems.getDefault().getPath(filePath);
//
//            // Проверяем существование директории и создаем её при необходимости
//            Path directory = path.getParent();
//            if (directory != null && !Files.exists(directory)) {
//                Files.createDirectories(directory); // Создание всех недостающих директорий
//            }
//
//            // Сохраняем QR-код в файл
//            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);
//        } catch (WriterException e) {
//            throw new RuntimeException("Failed to generate QR code", e);
//        }
//    }

    private static final String UPLOAD_API_URL = "https://warehouse-qr-app-8adwv.ondigitalocean.app/api/upload";

    /**
     * Генерация QR-кода и его загрузка на сервер через API
     */
    public static void generateQRCode(String text, String filePath) throws IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        try {
            // Генерация QR-кода
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);

            // Сохраняем QR-код в поток байтов
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", byteArrayOutputStream);
            byte[] qrCodeBytes = byteArrayOutputStream.toByteArray();

            // Подготовка HTTP-запроса
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE); // Используем метод add()
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(qrCodeBytes, headers);

            // Отправка запроса на сервер
            String response = restTemplate.postForObject(UPLOAD_API_URL, requestEntity, String.class);
            System.out.println("QR Code uploaded successfully: " + response);

        } catch (WriterException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }



}