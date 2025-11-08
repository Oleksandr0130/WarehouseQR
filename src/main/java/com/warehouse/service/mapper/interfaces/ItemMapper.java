package com.warehouse.service.mapper.interfaces;

import com.warehouse.model.Item;
import com.warehouse.model.dto.ItemDTO;
import org.mapstruct.*;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface ItemMapper {

    @Mapping(source = "qrCode", target = "qrCode", qualifiedByName = "mapQrCodeToString")
    ItemDTO toDTO(Item item);

    @Mapping(source = "qrCode", target = "qrCode", qualifiedByName = "mapStringToQrCode")
    Item toEntity(ItemDTO itemDTO);

    default List<ItemDTO> toDTOList(List<Item> items) {
        if (items == null) {
            System.err.println("Ошибка: пытаемся преобразовать null в список DTO.");
            return List.of(); // Возвращаем пустой список, если данные отсутствуют
        }

        // Логируем количество элементов перед началом преобразования
        System.out.println("Преобразование списка товаров в список DTO. Количество элементов: " + items.size());

        // Маппинг с фильтрацией null-элементов, чтобы защититься от ошибок
        return items.stream()
                .filter(Objects::nonNull) // Убираем возможные null-значения из списка
                .map(this::toDTO)         // Преобразуем в ItemDTO
                .toList();
    }


    // Метод для преобразования byte[] в Base64 String (помечаем @Named!)
    @Named("mapQrCodeToString")
    default String mapQrCodeToString(byte[] qrCode) {
        if (qrCode == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(qrCode);
    }

    // Метод для преобразования Base64 String в byte[] (помечаем @Named!)
    @Named("mapStringToQrCode")
    default byte[] mapStringToQrCode(String qrCode) {
        if (qrCode == null) {
            return null;
        }
        return Base64.getDecoder().decode(qrCode);
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(ItemDTO patch, @MappingTarget Item entity);
}


