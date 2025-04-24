package com.warehouse.controller;

import com.warehouse.model.Reservation;
import com.warehouse.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<Reservation> reserveItem(@RequestBody ReservationRequest request) {
        try {
            Reservation reservation = reservationService.reserveItem(
                    request.getOrderNumber(),
                    request.getItemName(),
                    request.getQuantity(),
                    request.getReservationWeek()
            );
            return ResponseEntity.ok(reservation);
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }


    @PostMapping("/reservations")
    public ResponseEntity<?> createReservations(@RequestBody List<Reservation> reservations) {
        // Логирование полученных данных
        System.out.println("Получен запрос: " + reservations);

        if (reservations == null || reservations.isEmpty()) {
            return ResponseEntity.badRequest().body("Список резервов пуст.");
        }

        // Обработка непустого массива
        return ResponseEntity.ok("Резервы успешно обработаны.");
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<String> completeReservation(@PathVariable Long id) {
        // Пример обработки. Логику можно изменить на свою
        boolean success = reservationService.completeReservation(id);
        if (success) {
            return ResponseEntity.ok("Reservation completed successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to complete reservation");
        }
    }



    @PostMapping("/scan")
    public ResponseEntity<String> processQRCode(@RequestParam String orderNumber) {
        try {
            reservationService.handleScannedQRCode(orderNumber);
            return ResponseEntity.ok("Item successfully sold");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Reservation>> getAllReservations(@RequestParam(required = false) String reservationWeek) {
        List<Reservation> reservations = reservationWeek == null
                ? reservationService.getAllReservations()
                : reservationService.getReservationsByWeek(reservationWeek);
        return ResponseEntity.ok(reservations);
    }

    /**
     * Получение зарезервированных товаров за указанную неделю с сортировкой по имени.
     */
    @GetMapping("/sorted")
    public ResponseEntity<List<Reservation>> getSortedReservationsByWeek(@RequestParam String reservationWeek) {
        List<Reservation> sortedReservations = reservationService.getSortedReservationsByWeek(reservationWeek);
        return ResponseEntity.ok(sortedReservations);
    }

}