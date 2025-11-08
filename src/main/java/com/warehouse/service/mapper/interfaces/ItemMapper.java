package com.warehouse.service.mapper.interfaces;

import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import org.mapstruct.*;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface ItemMapper {

    // Entity -> DTO
    @Mapping(source = "qrCode", target = "qrCode", qualifiedByName = "mapQrCodeToString")
    ItemDTO toDTO(Item item);

    // DTO -> Entity (CREATE)
    @Mappings({
            @Mapping(target = "company", ignore = true), // в DTO его нет
            @Mapping(source = "qrCode", target = "qrCode", qualifiedByName = "mapStringToQrCode")
    })
    Item toEntity(ItemDTO itemDTO);

    // Batch
    default List<ItemDTO> toDTOList(List<Item> items) {
        if (items == null) return List.of();
        return items.stream().filter(Objects::nonNull).map(this::toDTO).toList();
    }

    // PARTIAL UPDATE (PATCH/PUT subset)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mappings({
            @Mapping(target = "id", ignore = true),        // PK не трогаем
            @Mapping(target = "company", ignore = true),   // маппится не отсюда
            @Mapping(source = "qrCode", target = "qrCode", qualifiedByName = "mapStringToQrCode")
    })
    void updateEntityFromDto(ItemDTO patch, @MappingTarget Item entity);

    // Converters for qrCode
    @Named("mapQrCodeToString")
    default String mapQrCodeToString(byte[] qrCode) {
        return (qrCode == null || qrCode.length == 0) ? null : Base64.getEncoder().encodeToString(qrCode);
    }

    @Named("mapStringToQrCode")
    default byte[] mapStringToQrCode(String qrCode) {
        return (qrCode == null || qrCode.isBlank()) ? null : Base64.getDecoder().decode(qrCode);
    }
}
