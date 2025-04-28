package com.warehouse.service.mapper.interfaces;

import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ItemMapper {
    ItemDTO toDTO(Item item);
    Item toEntity(ItemDTO dto);
    List<ItemDTO> toDTOList(List<Item> items);
    List<Item> toEntityList(List<ItemDTO> dtos);
}

