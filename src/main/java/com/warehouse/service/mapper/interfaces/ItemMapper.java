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
    @Mapping(target = "qrCodeBase64", source = "qrCode", qualifiedByName = "mapQrCode")
    ItemDTO toDTO(Item item);

    @Mapping(target = "qrCode", source = "qrCodeBase64", qualifiedByName = "mapBase64ToQrCode")
    Item toEntity(ItemDTO dto);

    List<ItemDTO> toDTOList(List<Item> items);
    List<Item> toEntityList(List<ItemDTO> dtos);


    default String mapQrCode(byte[] qrCode) {
        return qrCode != null ? Base64.getEncoder().encodeToString(qrCode) : null;
    }

    default byte[] mapBase64ToQrCode(String base64QrCode) {
        return base64QrCode != null ? Base64.getDecoder().decode(base64QrCode) : null;
    }

}

