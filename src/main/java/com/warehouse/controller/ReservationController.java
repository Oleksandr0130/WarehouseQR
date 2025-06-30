package com.warehouse.controller;

import com.warehouse.model.Reservation;
import com.warehouse.model.dto.ReservationDTO;
import com.warehouse.model.dto.ReservationRequestDTO;
import com.warehouse.service.ReservationService;
import com.warehouse.service.mapper.interfaces.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationMapper reservationMapper;

    /**
     * Создание резервации.
     */
    @PostMapping
    public ResponseEntity<ReservationDTO> reserveItem(@RequestBody ReservationRequestDTO requestDTO) {
        try {
            var reservation = reservationService.reserveItem(
                    requestDTO.getOrderNumber(),
                    requestDTO.getItemName(),
                    requestDTO.getQuantity(),
                    requestDTO.getReservationWeek()
            );

            // Преобразование в DTO
            ReservationDTO responseDTO = reservationMapper.toDTO(reservation);
            responseDTO.setQrCode(Base64.getEncoder().encodeToString(reservation.getQrCode()));

            return ResponseEntity.ok(responseDTO);
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Создание списка резерваций.
     */
    @PostMapping("/batch")
    public ResponseEntity<String> createReservations(@RequestBody List<ReservationDTO> reservationsDTO) {
        if (reservationsDTO == null || reservationsDTO.isEmpty()) {
            return ResponseEntity.badRequest().body("Список резервов пуст.");
        }

        List<Reservation> reservations = reservationMapper.toEntityList(reservationsDTO);
        reservationService.saveAll(reservations);
        return ResponseEntity.ok("Список резервов успешно обработан.");
    }

    /**
     * Завершение резервации.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<String> completeReservation(@PathVariable Long id) {
        boolean success = reservationService.completeReservation(id);
        if (success) {
            return ResponseEntity.ok("Reservation completed successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to complete reservation");
        }
    }

    /**
     * Обработка сканирования QR-кода.
     */
    @PostMapping("/scan")
    public ResponseEntity<String> processQRCode(@RequestParam String orderNumber) {
        try {
            reservationService.handleScannedQRCode(orderNumber);
            return ResponseEntity.ok("Item successfully sold");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Получение всех резерваций или за конкретную неделю.
     */
    @GetMapping
    public ResponseEntity<List<ReservationDTO>> getAllReservations(@RequestParam(required = false) String reservationWeek) {
        List<Reservation> reservations = (reservationWeek == null)
                ? reservationService.getAllReservationsForCurrentCompany()
                : reservationService.getReservationsByWeekForCurrentCompany(reservationWeek);
        return ResponseEntity.ok(reservationMapper.toDTOList(reservations));
    }

    /**
     * Получение сортированных резерваций за определенную неделю.
     */
    @GetMapping("/sorted")
    public ResponseEntity<List<ReservationDTO>> getSortedReservationsByWeek(@RequestParam String reservationWeek) {
        List<Reservation> sortedReservations = reservationService.getSortedReservationsByWeek(reservationWeek);
        return ResponseEntity.ok(reservationMapper.toDTOList(sortedReservations));
    }

    /**
     * Поиск резерваций по префиксу номера заказа.
     */
    @GetMapping("/search/by-order-prefix")
    public ResponseEntity<List<ReservationDTO>> getReservationsByOrderPrefix(@RequestParam String orderPrefix) {
        if (orderPrefix == null || orderPrefix.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(List.of());
        }

        List<Reservation> reservations = reservationService.getReservationsByOrderPrefix(orderPrefix);
        if (reservations.isEmpty()) {
            return ResponseEntity.noContent().build(); // 204 No Content
        }

        return ResponseEntity.ok(reservationMapper.toDTOList(reservations));
    }

    /**
     * Удаление резервации.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteReservation(@PathVariable Long id) {
        try {
            Reservation removedReservation = reservationService.deleteReservation(id);
            String responseMessage = String.format(
                    "Reservation for order %s was removed. Returned quantity: %d to item '%s'.",
                    removedReservation.getOrderNumber(),
                    removedReservation.getReservedQuantity(),
                    removedReservation.getItemName()
            );
            return ResponseEntity.ok(responseMessage);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }
    }

    /**
     * Получение списка проданных резерваций.
     */
    @GetMapping("/sold")
    public ResponseEntity<List<ReservationDTO>> getSoldReservations() {
        List<Reservation> soldReservations = reservationService.getSoldReservations();
        return ResponseEntity.ok(reservationMapper.toDTOList(soldReservations));
    }

    /**
     * Скачивание QR-кода резервации.
     */
    @GetMapping("/{id}/download-qrcode")
    public ResponseEntity<byte[]> downloadQrCode(@PathVariable Long id) {
        try {
            Reservation reservation = reservationService.getReservationById(id);
            if (reservation == null || reservation.getQrCode() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/png")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            String.format("attachment; filename=%s_qrcode.png", reservation.getOrderNumber()))
                    .body(reservation.getQrCode());
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Поиск резерваций по названию товара.
     */
    @GetMapping("/search/by-item-name")
    public ResponseEntity<List<ReservationDTO>> searchReservationsByItemName(@RequestParam String itemName) {
        List<Reservation> reservations = reservationService.searchReservationsForCurrentCompany(itemName);
        return ResponseEntity.ok(reservationMapper.toDTOList(reservations));
    }
}