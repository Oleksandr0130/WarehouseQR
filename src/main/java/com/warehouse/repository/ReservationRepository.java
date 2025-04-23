package com.warehouse.repository;

import com.warehouse.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByReservationWeek(String reservationWeek);
    Optional<Reservation> findByOrderNumber(String orderNumber);

}