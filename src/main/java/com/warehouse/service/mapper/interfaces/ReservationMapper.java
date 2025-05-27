package com.warehouse.service.mapper.interfaces;

import com.warehouse.model.Reservation;
import com.warehouse.model.dto.ReservationDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Base64;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface ReservationMapper {
    // MapStruct не справляется с прямым маппингом byte[] -> String, добавляем кастомное преобразование
    @Mapping(target = "qrCode", expression = "java(mapQrCode(reservation.getQrCode()))")
    ReservationDTO toDTO(Reservation reservation);

    @Mapping(target = "qrCode", expression = "java(mapQrCode(dto.getQrCode()))")
    Reservation toEntity(ReservationDTO dto);

    // Для списков
    List<ReservationDTO> toDTOList(List<Reservation> reservations);
    List<Reservation> toEntityList(List<ReservationDTO> dtos);

    // Преобразование byte[] -> String (Base64)
    default String mapQrCode(byte[] qrCode) {
        return qrCode != null ? Base64.getEncoder().encodeToString(qrCode) : null;
    }

    // Преобразование String -> byte[] (Base64)
    default byte[] mapQrCode(String qrCode) {
        return qrCode != null ? Base64.getDecoder().decode(qrCode) : null;
    }

}
