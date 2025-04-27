package com.warehouse.service.mapper;

import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ItemMapper implements Mapper<Item, ItemDTO> {

    @Override
    public ItemDTO toDTO(Item item) {
        if (item == null) return null;
        ItemDTO dto = new ItemDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setQuantity(item.getQuantity());
        dto.setSold(item.getSold());
        return dto;
    }

    @Override
    public Item toEntity(ItemDTO dto) {
        if (dto == null) return null;
        Item item = new Item();
        item.setId(dto.getId());
        item.setName(dto.getName());
        item.setQuantity(dto.getQuantity());
        item.setSold(dto.getSold());
        return item;
    }

    @Override
    public List<ItemDTO> toDTOList(List<Item> items) {
        return items.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<Item> toEntityList(List<ItemDTO> dtos) {
        return dtos.stream().map(this::toEntity).collect(Collectors.toList());
    }
}
