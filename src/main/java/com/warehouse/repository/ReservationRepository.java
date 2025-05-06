package com.warehouse.repository;

import com.warehouse.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByReservationWeek(String reservationWeek);

    // Новый метод: Поиск по неделе с сортировкой по имени товара
    List<Reservation> findByReservationWeekOrderByItemName(String reservationWeek);

    Optional<Reservation> findByOrderNumber(String orderNumber);

    List<Reservation> findByStatus(String status);

}