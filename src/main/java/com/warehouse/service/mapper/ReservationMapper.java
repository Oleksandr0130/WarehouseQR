package com.warehouse.service.mapper;

import com.warehouse.model.Reservation;
import com.warehouse.model.dto.ReservationDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReservationMapper implements Mapper<Reservation, ReservationDTO> {

    @Override
    public ReservationDTO toDTO(Reservation reservation) {
        if (reservation == null) return null;
        ReservationDTO dto = new ReservationDTO();
        dto.setId(reservation.getId());
        dto.setOrderNumber(reservation.getOrderNumber());
        dto.setItemName(reservation.getItemName());
        dto.setReservedQuantity(reservation.getReservedQuantity());
        dto.setReservationWeek(reservation.getReservationWeek());
        dto.setStatus(reservation.getStatus());
        return dto;
    }

    @Override
    public Reservation toEntity(ReservationDTO dto) {
        if (dto == null) return null;
        Reservation reservation = new Reservation();
        reservation.setId(dto.getId());
        reservation.setOrderNumber(dto.getOrderNumber());
        reservation.setItemName(dto.getItemName());
        reservation.setReservedQuantity(dto.getReservedQuantity());
        reservation.setReservationWeek(dto.getReservationWeek());
        reservation.setStatus(dto.getStatus());
        return reservation;
    }

    @Override
    public List<ReservationDTO> toDTOList(List<Reservation> reservations) {
        return reservations.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<Reservation> toEntityList(List<ReservationDTO> dtos) {
        return dtos.stream().map(this::toEntity).collect(Collectors.toList());
    }
}

