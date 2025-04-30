package com.warehouse.controller;

import com.warehouse.model.dto.ItemDTO;
import com.warehouse.service.ItemService;
import com.warehouse.utils.ItemComparator;
import com.warehouse.service.mapper.interfaces.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        var itemEntity = itemMapper.toEntity(itemDTO);
        var savedItem = itemService.addItem(itemEntity);
        return ResponseEntity.ok(itemMapper.toDTO(savedItem));
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
}