package com.warehouse.service.mapper.interfaces;

import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.Base64;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface ItemMapper {
    ItemDTO toDTO(Item item);
    Item toEntity(ItemDTO dto);
    List<ItemDTO> toDTOList(List<Item> items);
    List<Item> toEntityList(List<ItemDTO> dtos);

    // Преобразование byte[] -> String (Base64)
    default String byteArrayToBase64(byte[] qrCode) {
        return qrCode != null ? Base64.getEncoder().encodeToString(qrCode) : null;
    }

    // Преобразование String -> byte[] (Base64)
    default byte[] base64ToByteArray(String qrCode) {
        return qrCode != null ? Base64.getDecoder().decode(qrCode) : null;
    }

}

