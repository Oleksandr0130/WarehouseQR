package com.warehouse.controller;

import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import com.warehouse.repository.ItemRepository;
import com.warehouse.service.ItemService;
import com.warehouse.utils.ItemComparator;
import com.warehouse.service.mapper.interfaces.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ItemController {
    private final ItemService itemService;
    private final ItemMapper itemMapper;
    private final ItemRepository itemRepository;


    @GetMapping("/{id}/qrcode")
    public ResponseEntity<byte[]> getQRCode(@PathVariable String id) {
        Optional<Item> itemOpt = itemRepository.findById(id);
        if (itemOpt.isPresent()) {
            Item item = itemOpt.get();

            // Проверяем, есть ли изображение QR-кода
            if (item.getQrCodeImage() != null) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Content-Type", "image/png");
                return new ResponseEntity<>(item.getQrCodeImage(), headers, HttpStatus.OK);
            } else {
                return ResponseEntity.notFound().build();
            }
        }
        return ResponseEntity.notFound().build();
    }


    @PostMapping
    public ResponseEntity<ItemDTO> addItem(@RequestBody ItemDTO itemDTO) {
        var itemEntity = itemMapper.toEntity(itemDTO);
        var savedItem = itemService.addItem(itemEntity);

        String qrCodeUrl = itemService.getQrCodeUrl(savedItem.getId()); // Генерация полного URL

        ItemDTO responseDTO = itemMapper.toDTO(savedItem);
        responseDTO.setQrCode(qrCodeUrl); // Убедитесь, что поле qrCode существует в ItemDTO

        return ResponseEntity.ok(responseDTO);
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

    @GetMapping
    public List<ItemDTO> getAllItems() {
        return itemMapper.toDTOList(itemService.getAllItems());
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