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
import jakarta.transaction.Transactional;
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
    private final UserService userService;
    private final ItemMapper itemMapper;

//    @Value("${app.qrcode-base-url}")
@Value("${app.qrcode-base-url}")
private String qrCodeBaseUrl; // Значение из application.yml

    public ItemService(ItemRepository itemRepository, ReservationRepository reservationRepository, ItemMapper itemMapper, UserService userService) {
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
//        return qrCodeBaseUrl + id + ".png"; // Формирование полного URL
        throw new UnsupportedOperationException("QR-коды хранятся в базе данных в формате Base64. Используйте Base64 строку.");

    }


//    public Item addItem(Item item) {
//        if (item.getId() == null || item.getId().isEmpty()) {
//            item.setId(UUID.randomUUID().toString());
//        }
//        Item savedItem = itemRepository.save(item);
//        // Генерация QR-кода
//        generateQRCode(savedItem.getId());
//
//        // Установка QR-кода в объект и повторное сохранение
//        savedItem.setQrCode(getQrCodeUrl(savedItem.getId()));
//        return itemRepository.save(savedItem); // Сохраняем с обновленным полем qrCode
//
//    }
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

        // Генерация QR-кода
        byte[] qrCodeBytes = generateQRCodeAsBytes(savedItem.getId());
        savedItem.setQrCode(qrCodeBytes);

        return itemRepository.save(savedItem);
    } catch (Exception e) {
        System.err.println("Ошибка при добавлении товара: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException("Не удалось добавить товар. Обратитесь к администратору.", e);
    }
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

@Transactional
    public List<Item> getAllItems() {
    try {
        // Получаем текущего пользователя
        var currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException("Текущий пользователь не найден. Необходимо выполнить вход.");
        }

        // Получаем компанию пользователя
        Company currentCompany = currentUser.getCompany();
        if (currentCompany == null) {
            throw new IllegalStateException("Компания текущего пользователя не определена. Проверьте настройки пользователя.");
        }

        System.out.println("Текущая компания ID: " + currentCompany.getId());

// Загружаем товары
        List<Item> items = itemRepository.findAllByCompany(currentCompany);

        if (items == null) {
            // Логируем, если вдруг случилось нечто необычное и элементы оказались null
            System.err.println("Ошибка: метод findAllByCompany вернул null вместо пустого списка.");
            return List.of(); // Возвращаем пустой список, чтобы избежать NullPointerException
        }

        if (items.isEmpty()) {
            // Логируем, если нет подходящих товаров
            System.out.println("Нет доступных товаров для компании ID: " + currentCompany.getId());
        }

        return items;

    } catch (IllegalStateException e) {
        // Логируем ошибки состояния
        System.err.println("Ошибка состояния при загрузке товаров: " + e.getMessage());
        throw e;
    } catch (Exception e) {
        // Любые другие ошибки
        System.err.println("Произошла ошибка при загрузке товаров: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException("Ошибка при загрузке товаров. Обратитесь к администратору.", e);
    }
}



    @Transactional
    public Optional<Item> getItemByName(String name) {
        Company currentCompany = userService.getCurrentUser().getCompany();
        return itemRepository.findByNameAndCompany(name, currentCompany);
    }


    public List<Item> getAllItemsSorted(Comparator<Item> comparator) {
        List<Item> items = getAllItems(); // Получаем все товары
        items.sort(comparator); // Сортируем с использованием переданного компаратора
        return items;
    }


//    private void generateQRCode(String id) {
//        try {
//            // Создаем полный путь до папки, включая вложенные директории
//            Path qrFolderPath = Paths.get(QR_PATH + id).getParent();
//            if (qrFolderPath != null) {
//                Files.createDirectories(qrFolderPath);
//            }
//
//            // Формируем полный путь к файлу
//            String filePath = QR_PATH + id + ".png";
//
//            // Генерация и сохранение QR-кода
//            QRCodeWriter qrCodeWriter = new QRCodeWriter();
//            BitMatrix bitMatrix = qrCodeWriter.encode(id, BarcodeFormat.QR_CODE, 200, 200);
//            Path path = Paths.get(filePath);
//            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);
//
//        } catch (WriterException | IOException e) {
//            throw new RuntimeException("Failed to generate QR code: " + e.getMessage());
//        }
//    }
// Генерация QR-кода в виде массива байт
private byte[] generateQRCodeAsBytes(String id) {
    try {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(id, BarcodeFormat.QR_CODE, 200, 200);

        // Пишем данные QR-кода в ByteArrayOutputStream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

        return outputStream.toByteArray(); // Возвращаем массив байт
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

            // Создаем заголовок таблицы
            Row headerRow = sheet.createRow(0);
            String[] columns = {"ID", "Name", "Quantity"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
            }

            // Заполняем данные товаров
            int rowNum = 1;
            for (Item item : items) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getId());
                row.createCell(1).setCellValue(item.getName());
                row.createCell(2).setCellValue(item.getQuantity());
            }

            // Сохраняем файл в поток
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Excel file", e);
        }
    }

}
