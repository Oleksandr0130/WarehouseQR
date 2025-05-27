package com.warehouse.service.mapper.interfaces;

import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Base64;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface ItemMapper {
    @Mapping(source = "qrCode", target = "qrCode", qualifiedByName = "mapQrCodeToString")
    ItemDTO toDTO(Item item);

    @Mapping(source = "qrCode", target = "qrCode", qualifiedByName = "mapStringToQrCode")
    Item toEntity(ItemDTO itemDTO);

    List<ItemDTO> toDTOList(List<Item> items);

    // Метод для преобразования byte[] в Base64 String (для DTO)
    default String mapQrCodeToString(byte[] qrCode) {
        if (qrCode == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(qrCode);
    }

    // Метод для преобразования Base64 String в byte[] (для сущности Item)
    default byte[] mapStringToQrCode(String qrCode) {
        if (qrCode == null) {
            return null;
        }
        return Base64.getDecoder().decode(qrCode);
    }

}

