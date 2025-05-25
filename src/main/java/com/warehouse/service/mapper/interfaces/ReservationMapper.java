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
    @Mapping(target = "qrCodeBase64", source = "qrCode", qualifiedByName = "mapQrCode")
    ReservationDTO toDTO(Reservation reservation);

    @Mapping(target = "qrCode", source = "qrCodeBase64", qualifiedByName = "mapBase64ToQrCode")
    Reservation toEntity(ReservationDTO dto);

    List<ReservationDTO> toDTOList(List<Reservation> reservations);
    List<Reservation> toEntityList(List<ReservationDTO> dtos);

    default String mapQrCode(byte[] qrCode) {
        return qrCode != null ? Base64.getEncoder().encodeToString(qrCode) : null;
    }

    default byte[] mapBase64ToQrCode(String base64QrCode) {
        return base64QrCode != null ? Base64.getDecoder().decode(base64QrCode) : null;
    }

}

