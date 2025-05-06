package com.warehouse.repository;

import com.warehouse.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByReservationWeek(String reservationWeek);

    // Новый метод: Поиск по неделе с сортировкой по имени товара
    List<Reservation> findByReservationWeekOrderByItemName(String reservationWeek);

    Optional<Reservation> findByOrderNumber(String orderNumber);

    @Query("SELECT SUM(r.reservedQuantity) FROM Reservation r WHERE r.itemName = :itemName AND r.status = 'SOLD'")
    Optional<Integer> getTotalSoldQuantityForItem(@Param("itemName") String itemName);

}