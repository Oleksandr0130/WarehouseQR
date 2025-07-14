package com.warehouse.repository;

import com.warehouse.model.Company;
import com.warehouse.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByReservationWeek(String reservationWeek);

    // Новый метод: Поиск по неделе с сортировкой по имени товара
    @Query("SELECT r FROM Reservation r WHERE r.reservationWeek = :reservationWeek AND r.status = 'RESERVED' AND r.company = :company ORDER BY r.itemName")
    List<Reservation> findByReservationWeekAndCompanyOrderByItemName(@Param("reservationWeek") String reservationWeek, @Param("company") Company company);


    @Query("SELECT r FROM Reservation r WHERE r.orderNumber LIKE CONCAT(:orderPrefix, '%') AND r.status = 'RESERVED' AND r.company = :company")
    List<Reservation> findByOrderNumberStartingWithAndCompany(@Param("orderPrefix") String orderPrefix, @Param("company") Company company);



    Optional<Reservation> findByOrderNumber(String orderNumber);

    @Query("SELECT SUM(r.reservedQuantity) FROM Reservation r WHERE r.itemName = :itemName AND r.status = 'SOLD'")
    Optional<Integer> getTotalSoldQuantityForItem(@Param("itemName") String itemName);

    @Query("SELECT r FROM Reservation r WHERE LOWER(r.itemName) LIKE LOWER(CONCAT('%', :searchQuery, '%')) AND r.company = :company")
    List<Reservation> findByItemNameContainingIgnoreCaseAndCompany(@Param("searchQuery") String searchQuery, @Param("company") Company company);

    @Query("SELECT r FROM Reservation r WHERE r.company = :company")
    List<Reservation> findByCompany(@Param("company") Company company);

}