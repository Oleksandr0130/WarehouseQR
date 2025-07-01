package com.warehouse.controller;

import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import com.warehouse.service.CurrentCompanyService;
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
    private final CurrentCompanyService currentCompanyService;



    @PostMapping
    public ResponseEntity<ItemDTO> addItem(@RequestBody ItemDTO itemDTO) {
        var itemEntity = itemMapper.toEntity(itemDTO); // Маппинг DTO в сущность
        var savedItem = itemService.addItem(itemEntity); // Сохранение в базе

        // Генерация QR-кода Base64
        String qrCodeBase64 = itemMapper.mapQrCodeToString(savedItem.getQrCode()); // Преобразуем byte[] в Base64

        ItemDTO responseDTO = itemMapper.toDTO(savedItem);
        responseDTO.setQrCode(qrCodeBase64); // Устанавливаем строку Base64 в итоговый ответ

        return ResponseEntity.ok(responseDTO);
    }


    // Новый endpoint: скачивание QR-кода
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
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // Вернуть 404, если QR-код не найден
        }
    }




    @PutMapping("/{id}/add")
    public ResponseEntity<ItemDTO> addQuantity(@PathVariable("id") String id, @RequestParam("quantity") int quantity) {
        return itemService.updateQuantity(id, quantity)
                .map(item -> ResponseEntity.ok(itemMapper.toDTO(item)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/remove")
    public ResponseEntity<ItemDTO> removeQuantity(@PathVariable("id") String id, @RequestParam("quantity") int quantity) {
        return itemService.removeQuantity(id, quantity)
                .map(item -> ResponseEntity.ok(itemMapper.toDTO(item)))
                .orElse(ResponseEntity.badRequest().build());
    }

//    @GetMapping
//    public List<ItemDTO> getAllItems() {
//        return itemMapper.toDTOList(itemService.getAllItems());
//    }

    @GetMapping
    public List<ItemDTO> getAllItems() {
        Long companyId = currentCompanyService.getCurrentCompanyId();
        return itemMapper.toDTOList(itemService.getAllItemsByCompany(companyId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemDTO> getItem(@PathVariable String id) {
        Long companyId = currentCompanyService.getCurrentCompanyId();
        return itemService.getItemByIdAndCompanyId(id, companyId)
                .map(item -> ResponseEntity.ok(itemMapper.toDTO(item)))
                .orElse(ResponseEntity.notFound().build());
    }



    @GetMapping("/sorted")
    public List<ItemDTO> getSortedItems(@RequestParam("sortBy") String sortBy) {
        switch (sortBy.toLowerCase()) {
            case "name":
                return itemMapper.toDTOList(itemService.getAllItemsSorted(ItemComparator.BY_NAME));
            case "quantity":
                return itemMapper.toDTOList(itemService.getAllItemsSorted(ItemComparator.BY_QUANTITY));
            case "sold":
                return itemMapper.toDTOList(itemService.getAllItemsSorted(ItemComparator.BY_SOLD));
            default:
                throw new IllegalArgumentException("Invalid sortBy parameter. Use 'name', 'quantity', or 'sold'.");
        }
    }

    // API для получения всего списка товаров с данными о продажах
    @GetMapping("/sold")
    public ResponseEntity<List<ItemDTO>> getAllItemsWithSoldData() {
        List<ItemDTO> items = itemService.getAllItemsWithSoldData();
        return ResponseEntity.ok(items);
    }

    // API для получения количества проданных единиц для конкретного товара
    @GetMapping("/{id}/sold")
    public ResponseEntity<Integer> getSoldQuantityForItem(@PathVariable String id) {
        try {
            int soldQuantity = itemService.getSoldQuantityForItem(id);
            return ResponseEntity.ok(soldQuantity);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/download/excel")
    public ResponseEntity<InputStreamResource> downloadExcelFile() {
        List<Item> items = itemService.getAllItems(); // Получение всех товаров
        InputStream excelFile = itemService.generateExcelFile(items); // Генерация Excel-файла

        // Настройка заголовков ответа
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=items.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(excelFile));
    }

}