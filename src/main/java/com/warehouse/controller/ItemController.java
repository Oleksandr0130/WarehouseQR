package com.warehouse.controller;

import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import com.warehouse.service.ItemService;
import com.warehouse.utils.ItemComparator;
import com.warehouse.service.mapper.interfaces.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ItemController {
    private final ItemService itemService;
    private final ItemMapper itemMapper;

    @PostMapping
    public ResponseEntity<ItemDTO> addItem(@RequestBody ItemDTO itemDTO) {
        try {
            var itemEntity = itemMapper.toEntity(itemDTO);
            var savedItem = itemService.addItem(itemEntity);
            String qrCodeBase64 = itemMapper.mapQrCodeToString(savedItem.getQrCode());
            ItemDTO responseDTO = itemMapper.toDTO(savedItem);
            responseDTO.setQrCode(qrCodeBase64);
            return ResponseEntity.ok(responseDTO);
        } catch (IllegalArgumentException e) {
            // Логируем ошибку перед возвратом BAD_REQUEST
            e.printStackTrace();
            throw new IllegalArgumentException("Ошибка при добавлении товара: " + e.getMessage(), e);
        } catch (Exception e) {
            // Для остальных исключений пробрасываем ошибку, чтобы обработать в глобальном обработчике
            e.printStackTrace();
            throw new RuntimeException("Произошла ошибка при обработке запроса на добавление товара", e);
        }
    }

    @GetMapping("/{id}/download-qrcode")
    public ResponseEntity<ByteArrayResource> downloadQRCode(@PathVariable String id) {
        try {
            byte[] qrCodeBytes = itemService.getQRCode(id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + id + ".png")
                    .contentType(MediaType.IMAGE_PNG)
                    .contentLength(qrCodeBytes.length)
                    .body(new ByteArrayResource(qrCodeBytes));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("QR-код для товара с ID " + id + " не найден. Причина: " + e.getMessage(), e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Ошибка при загрузке QR-кода для товара ID: " + id, e);
        }
    }

    @PutMapping("/{id}/add")
    public ResponseEntity<ItemDTO> addQuantity(@PathVariable("id") String id, @RequestParam("quantity") int quantity) {
        try {
            return itemService.updateQuantity(id, quantity)
                    .map(item -> ResponseEntity.ok(itemMapper.toDTO(item)))
                    .orElseThrow(() -> new IllegalArgumentException("Товар с ID " + id + " не найден."));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ошибка при попытке увеличить количество товара с ID " + id + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Серверная ошибка при увеличении количества. ID товара: " + id, e);
        }
    }

    @PutMapping("/{id}/remove")
    public ResponseEntity<ItemDTO> removeQuantity(@PathVariable("id") String id, @RequestParam("quantity") int quantity) {
        try {
            return itemService.removeQuantity(id, quantity)
                    .map(item -> ResponseEntity.ok(itemMapper.toDTO(item)))
                    .orElseThrow(() -> new IllegalArgumentException("Товар с ID " + id + " не найден или недостаточное количество для снятия."));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ошибка при попытке уменьшить количество товара с ID " + id + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Серверная ошибка при уменьшении количества товара. ID товара: " + id, e);
        }
    }

    @GetMapping
    public List<ItemDTO> getAllItems() {
        try {
            List<Item> items = itemService.getAllItems();
            return itemMapper.toDTOList(items);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при получении всех товаров.", e);
        }
    }

    @GetMapping("/sorted")
    public List<ItemDTO> getSortedItems(@RequestParam("sortBy") String sortBy) {
        try {
            switch (sortBy.toLowerCase()) {
                case "name":
                    return itemMapper.toDTOList(itemService.getAllItemsSorted(ItemComparator.BY_NAME));
                case "quantity":
                    return itemMapper.toDTOList(itemService.getAllItemsSorted(ItemComparator.BY_QUANTITY));
                case "sold":
                    return itemMapper.toDTOList(itemService.getAllItemsSorted(ItemComparator.BY_SOLD));
                default:
                    throw new IllegalArgumentException("Параметр sortBy недействителен. Используйте 'name', 'quantity' или 'sold'.");
            }
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage(), e
            );
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сортировки", e
            );
        }
    }

    @GetMapping("/sold")
    public ResponseEntity<List<ItemDTO>> getAllItemsWithSoldData() {
        try {
            List<ItemDTO> items = itemService.getAllItemsWithSoldData();
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при получении товаров с данными о продажах.", e);
        }
    }

    @GetMapping("/{id}/sold")
    public ResponseEntity<Integer> getSoldQuantityForItem(@PathVariable String id) {
        try {
            int soldQuantity = itemService.getSoldQuantityForItem(id);
            return ResponseEntity.ok(soldQuantity);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ошибка при получении данных о проданных единицах товара с ID: " + id + ". Причина: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обработке запроса на проданные единицы товара. ID товара: " + id, e);
        }
    }

    @GetMapping("/download/excel")
    public ResponseEntity<InputStreamResource> downloadExcelFile() {
        try {
            List<Item> items = itemService.getAllItems();
            InputStream excelFile = itemService.generateExcelFile(items);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=items.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(excelFile));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при генерации Excel-файла с товарами.", e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        try {
            itemService.deleteItem(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            // Если хочешь — можно вернуть 404
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ItemDTO> update(@PathVariable String id, @RequestBody ItemDTO patch) {
        ItemDTO updated = itemService.updateItem(id, patch);
        return ResponseEntity.ok(updated);
    }

}