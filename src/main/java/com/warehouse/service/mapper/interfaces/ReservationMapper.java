package com.warehouse.service.mapper.interfaces;

import com.warehouse.model.Reservation;
import com.warehouse.model.dto.ReservationDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReservationMapper {
    ReservationDTO toDTO(Reservation reservation);
    Reservation toEntity(ReservationDTO dto);
    List<ReservationDTO> toDTOList(List<Reservation> reservations);
    List<Reservation> toEntityList(List<ReservationDTO> dtos);
}
