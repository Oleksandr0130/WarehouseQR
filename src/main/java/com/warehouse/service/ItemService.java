package com.warehouse.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import com.warehouse.repository.ItemRepository;
import com.warehouse.repository.ReservationRepository;
import com.warehouse.service.mapper.interfaces.ItemMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ItemService {
    private static final String QR_PATH = "qrcodes/";
    private final ItemRepository itemRepository;
    private final ReservationRepository reservationRepository;
    private final ItemMapper itemMapper;

    @Value("${app.qrcode-base-url}")
    private String qrCodeBaseUrl; // Значение из application.yml

    public ItemService(ItemRepository itemRepository, ReservationRepository reservationRepository, ItemMapper itemMapper) {
        this.itemRepository = itemRepository;
        this.reservationRepository = reservationRepository;
        this.itemMapper = itemMapper;
        try {
            Files.createDirectories(Paths.get(QR_PATH));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create QR codes directory: " + QR_PATH, e);
        }
    }


    public String getQrCodeUrl(String id) {
        return qrCodeBaseUrl + id + ".png"; // Формирование полного URL
    }


    public Item addItem(Item item) {
        if (item.getId() == null || item.getId().isEmpty()) {
            item.setId(UUID.randomUUID().toString());
        }
        Item savedItem = itemRepository.save(item);
        generateQRCode(item.getId());
        return savedItem;
    }

    public Optional<Item> updateQuantity(String id, int quantity) {
        Optional<Item> itemOpt = itemRepository.findById(id);
        if (itemOpt.isPresent()) {
            Item item = itemOpt.get();
            item.setQuantity(item.getQuantity() + quantity);
            return Optional.of(itemRepository.save(item));
        }
        return Optional.empty();
    }

    public Optional<Item> removeQuantity(String id, int quantity) {
        Optional<Item> itemOpt = itemRepository.findById(id);
        if (itemOpt.isPresent()) {
            Item item = itemOpt.get();
            if (item.getQuantity() >= quantity) {
                item.setQuantity(item.getQuantity() - quantity);
                item.setSold(item.getSold() + quantity);
                return Optional.of(itemRepository.save(item));
            }
        }
        return Optional.empty();
    }

    // Метод для получения количества проданных товаров
    public int getSoldQuantityForItem(String itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found for ID: " + itemId));

        // Суммируем из Reservation по имени товара
        return reservationRepository.getTotalSoldQuantityForItem(item.getName())
                .orElse(0); // Если в таблице Reservation нет данных - возвращаем 0
    }

    // Новый метод: Вернуть список всех товаров с подсчётом проданных штук
    public List<ItemDTO> getAllItemsWithSoldData() {
        List<Item> items = itemRepository.findAll();

        List<ItemDTO> itemDTOs = itemMapper.toDTOList(items);
        itemDTOs.forEach(itemDTO -> {
            int soldQuantity = reservationRepository.getTotalSoldQuantityForItem(itemDTO.getName()).orElse(0);
            itemDTO.setSold(soldQuantity);
        });

        return itemDTOs;
    }


    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    public List<Item> getAllItemsSorted(Comparator<Item> comparator) {
        List<Item> items = getAllItems(); // Получаем все товары
        items.sort(comparator); // Сортируем с использованием переданного компаратора
        return items;
    }


    private void generateQRCode(String id) {
        try {
            // Создаем полный путь до папки, включая вложенные директории
            Path qrFolderPath = Paths.get(QR_PATH + id).getParent();
            if (qrFolderPath != null) {
                Files.createDirectories(qrFolderPath);
            }

            // Формируем полный путь к файлу
            String filePath = QR_PATH + id + ".png";

            // Генерация и сохранение QR-кода
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(id, BarcodeFormat.QR_CODE, 200, 200);
            Path path = Paths.get(filePath);
            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);

        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code: " + e.getMessage());
        }
    }
}
