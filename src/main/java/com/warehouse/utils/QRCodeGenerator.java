package com.warehouse.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class QRCodeGenerator {

    public static void generateQRCode(String text, String filePath) throws IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);

            // Получаем путь для файла
            Path path = FileSystems.getDefault().getPath(filePath);

            // Проверяем существование директории и создаем её при необходимости
            Path directory = path.getParent();
            if (directory != null && !Files.exists(directory)) {
                Files.createDirectories(directory); // Создание всех недостающих директорий
            }

            // Сохраняем QR-код в файл
            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);
        } catch (WriterException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}