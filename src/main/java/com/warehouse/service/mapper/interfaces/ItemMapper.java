package com.warehouse.service.mapper.interfaces;

import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import org.mapstruct.*;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface ItemMapper {

    /* ======== Entity -> DTO ======== */
    @Mapping(source = "qrCode", target = "qrCode", qualifiedByName = "mapQrCodeToString")
    ItemDTO toDTO(Item item);

    /* ======== DTO -> Entity (create) ======== */
    @Mapping(source = "qrCode", target = "qrCode", qualifiedByName = "mapStringToQrCode")
    Item toEntity(ItemDTO itemDTO);

    /* ======== Частичное обновление Entity из DTO (PUT /items/{id}) ========
       IGNORE = не перезаписывать поля null-значениями.
       Пустой список images из DTO заменит текущие картинки (т.е. можно очистить).
    */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "qrCode", target = "qrCode", qualifiedByName = "mapStringToQrCode")
    void updateEntityFromDto(ItemDTO patch, @MappingTarget Item entity);

    /* ======== Списки ======== */
    default List<ItemDTO> toDTOList(List<Item> items) {
        if (items == null) {
            System.err.println("Ошибка: пытаемся преобразовать null в список DTO.");
            return List.of();
        }
        System.out.println("Преобразование списка товаров в список DTO. Количество элементов: " + items.size());
        return items.stream()
                .filter(Objects::nonNull)
                .map(this::toDTO)
                .toList();
    }

    /* ======== QR helpers ======== */
    @Named("mapQrCodeToString")
    default String mapQrCodeToString(byte[] qrCode) {
        if (qrCode == null) return null;
        return Base64.getEncoder().encodeToString(qrCode);
    }

    @Named("mapStringToQrCode")
    default byte[] mapStringToQrCode(String qrCode) {
        if (qrCode == null || qrCode.isBlank()) return null;
        return Base64.getDecoder().decode(qrCode);
    }
}
