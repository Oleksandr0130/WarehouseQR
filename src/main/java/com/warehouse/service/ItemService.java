package com.warehouse.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warehouse.model.Company;
import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import com.warehouse.repository.ItemRepository;
import com.warehouse.repository.ReservationRepository;
import com.warehouse.service.mapper.interfaces.ItemMapper;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
    private final UserService userService;
    private final ItemMapper itemMapper;

    @Value("${app.qrcode-base-url}")
    private String qrCodeBaseUrl; // Значение из application.yml

    public ItemService(ItemRepository itemRepository,
                       ReservationRepository reservationRepository,
                       ItemMapper itemMapper,
                       UserService userService) {
        this.itemRepository = itemRepository;
        this.reservationRepository = reservationRepository;
        this.userService = userService;
        this.itemMapper = itemMapper;
        try {
            Files.createDirectories(Paths.get(QR_PATH));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create QR codes directory: " + QR_PATH, e);
        }
    }

    public String getQrCodeUrl(String id) {
        // return qrCodeBaseUrl + id + ".png";
        throw new UnsupportedOperationException("QR-коды хранятся в базе данных в формате Base64. Используйте Base64 строку.");
    }

    @Transactional
    public Item addItem(Item item) {
        try {
            var currentUser = userService.getCurrentUser();
            if (currentUser == null) {
                throw new IllegalStateException("Текущий пользователь не найден.");
            }

            Company currentCompany = currentUser.getCompany();
            if (currentCompany == null) {
                throw new IllegalStateException("Компания текущего пользователя не определена.");
            }

            // Устанавливаем текущую компанию
            item.setCompany(currentCompany);

            if (item.getId() == null || item.getId().isEmpty()) {
                item.setId(UUID.randomUUID().toString());
            }

            Item savedItem = itemRepository.save(item);

            // Генерация QR-кода (в bytes)
            byte[] qrCodeBytes = generateQRCodeAsBytes(savedItem.getId());
            savedItem.setQrCode(qrCodeBytes);

            return itemRepository.save(savedItem);
        } catch (Exception e) {
            System.err.println("Ошибка при добавлении товара: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Не удалось добавить товар. Обратитесь к администратору.", e);
        }
    }

    @Transactional
    public void deleteItem(String id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found for ID: " + id));
        if (item.getImages() != null && !item.getImages().isEmpty()) {
            item.getImages().clear();
        }
        itemRepository.deleteById(id);
    }

    /* ========================= NEW: Частичное обновление товара =========================
       Метод вызывается контроллером из PUT /items/{id}.
       - копируем ТОЛЬКО не-null поля из DTO в entity (это делает ItemMapper#updateEntityFromDto)
       - если пришёл images != null, заменяем коллекцию (позволяет очистить, если []),
       - сохраняем и отдаём обновлённый DTO.
     */
    @Transactional
    public ItemDTO updateItem(String id, ItemDTO patch) {
        Item entity = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found for ID: " + id));

        // Копируем только присланные поля (description/price/currency и пр.)
        itemMapper.updateEntityFromDto(patch, entity);

        // ВАЖНО: именно так позволяем "стереть" картинки — если пришёл пустой список,
        // он заменит существующую коллекцию; если пришёл null — оставим как было.
        if (patch.getImages() != null) {
            entity.setImages(patch.getImages());
        }

        // Если на фронте убрали цену (price=null), можно дополнительно обнулить валюту:
        // if (patch.getPrice() == null) entity.setCurrency(null);

        Item saved = itemRepository.save(entity);
        return itemMapper.toDTO(saved);
    }
    /* ================================================================================== */

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
                .orElse(0);
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

    @Transactional
    public List<Item> getAllItems() {
        try {
            System.out.println("Вызов ItemService.getAllItems() начат.");

            var currentUser = userService.getCurrentUser();
            if (currentUser == null) {
                throw new IllegalStateException("Текущий пользователь не определен. Авторизация отсутствует.");
            }

            Company currentCompany = currentUser.getCompany();
            if (currentCompany == null) {
                throw new IllegalStateException("Компания текущего пользователя не определена. Свяжите пользователя с компанией.");
            }

            System.out.println("Компания пользователя: ID = " + currentCompany.getId() + ", Name = " + currentCompany.getName());

            List<Item> items = itemRepository.findAllByCompany(currentCompany);

            if (items == null) {
                System.err.println("findAllByCompany вернул null для компании ID: " + currentCompany.getId());
                return List.of();
            }
            System.out.println("Найдено товаров: " + items.size());

            if (items.isEmpty()) {
                System.out.println("Для компании ID: " + currentCompany.getId() + " товары отсутствуют.");
            }

            return items;
        } catch (IllegalStateException e) {
            System.err.println("Ошибка состояния: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Внутренняя ошибка при загрузке товаров: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Ошибка при загрузке товаров. Подробности в логах сервера.", e);
        }
    }

    @Transactional
    public Optional<Item> getItemByName(String name) {
        Company currentCompany = userService.getCurrentUser().getCompany();
        return itemRepository.findByNameAndCompany(name, currentCompany);
    }

    // ItemService.java
    public List<Item> getAllItemsSorted(Comparator<Item> comparator) {
        List<Item> items = getAllItems();
        items.sort(Comparator.nullsLast(comparator));
        return items;
    }

    // Генерация QR-кода в виде массива байт
    private byte[] generateQRCodeAsBytes(String id) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(id, BarcodeFormat.QR_CODE, 200, 200);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code: " + e.getMessage());
        }
    }

    // Возвращаем QR-код из базы в виде массива байт
    public byte[] getQRCode(String itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found for ID: " + itemId));
        return item.getQrCode();
    }

    public InputStream generateExcelFile(List<Item> items) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Items");

            Row headerRow = sheet.createRow(0);
            String[] columns = {"ID", "Name", "Quantity"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
            }

            int rowNum = 1;
            for (Item item : items) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getId());
                row.createCell(1).setCellValue(item.getName());
                row.createCell(2).setCellValue(item.getQuantity());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Excel file", e);
        }
    }
}
