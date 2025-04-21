package com.warehouse.controller;

import com.warehouse.model.Item;
import com.warehouse.service.ItemService;
import com.warehouse.utils.ItemComparator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "http://localhost:5173")
public class ItemController {
    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    public ResponseEntity<Item> addItem(@RequestBody Item item) {
        return ResponseEntity.ok(itemService.addItem(item));
    }

    @PutMapping("/{id}/add")
    public ResponseEntity<Item> addQuantity(@PathVariable("id") String id, @RequestParam("quantity") int quantity) {
        return itemService.updateQuantity(id, quantity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/remove")
    public ResponseEntity<Item> removeQuantity(@PathVariable("id") String id, @RequestParam("quantity") int quantity) {
        return itemService.removeQuantity(id, quantity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @GetMapping
    public List<Item> getAllItems() {
        return itemService.getAllItems();
    }

    @GetMapping("/sorted")
    public List<Item> getSortedItems(@RequestParam("sortBy") String sortBy) {
        switch (sortBy.toLowerCase()) {
            case "name":
                return itemService.getAllItemsSorted(ItemComparator.BY_NAME);
            case "quantity":
                return itemService.getAllItemsSorted(ItemComparator.BY_QUANTITY);
            case "sold":
                return itemService.getAllItemsSorted(ItemComparator.BY_SOLD);
            default:
                throw new IllegalArgumentException("Invalid sortBy parameter. Use 'name', 'quantity', or 'sold'.");
        }
    }
}
