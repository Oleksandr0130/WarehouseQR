package com.warehouse.controller;

import com.warehouse.model.Reservation;
import com.warehouse.model.dto.ReservationDTO;
import com.warehouse.model.dto.ReservationRequestDTO;
import com.warehouse.service.ReservationService;
import com.warehouse.service.mapper.interfaces.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationMapper reservationMapper;

    @PostMapping
    public ResponseEntity<ReservationDTO> reserveItem(@RequestBody ReservationRequestDTO requestDTO) {
        try {
            var reservation = reservationService.reserveItem(
                    requestDTO.getOrderNumber(),
                    requestDTO.getItemName(),
                    requestDTO.getQuantity(),
                    requestDTO.getReservationWeek()
            );

            String qrCodeUrl = reservationService.getReservationQrUrl(reservation.getOrderNumber()); // Генерация полного URL

            ReservationDTO responseDTO = reservationMapper.toDTO(reservation);
            responseDTO.setQrCode(qrCodeUrl); // Убедитесь, что поле qrCode существует в ReservationDTO

            return ResponseEntity.ok(responseDTO);
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }


    @PostMapping("/reservations")
    public ResponseEntity<String> createReservations(@RequestBody List<ReservationDTO> reservationsDTO) {
        if (reservationsDTO == null || reservationsDTO.isEmpty()) {
            return ResponseEntity.badRequest().body("Список резервов пуст.");
        }

        List<Reservation> reservations = reservationMapper.toEntityList(reservationsDTO);
        reservationService.saveAll(reservations);
        return ResponseEntity.ok("Список резервов успешно обработан.");
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<String> completeReservation(@PathVariable Long id) {
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
    public ResponseEntity<List<ReservationDTO>> getAllReservations(@RequestParam(required = false) String reservationWeek) {
        List<Reservation> reservations = (reservationWeek == null)
                ? reservationService.getAllReservations()
                : reservationService.getReservationsByWeek(reservationWeek);
        return ResponseEntity.ok(reservationMapper.toDTOList(reservations));
    }

    @GetMapping("/sorted")
    public ResponseEntity<List<ReservationDTO>> getSortedReservationsByWeek(@RequestParam String reservationWeek) {
        List<Reservation> sortedReservations = reservationService.getSortedReservationsByWeek(reservationWeek);
        return ResponseEntity.ok(reservationMapper.toDTOList(sortedReservations));
    }

    @GetMapping("/search/by-order-prefix")
    public ResponseEntity<List<ReservationDTO>> getReservationsByOrderPrefix(@RequestParam String orderPrefix) {
        List<Reservation> reservations = reservationService.getReservationsByOrderPrefix(orderPrefix);
        return ResponseEntity.ok(reservationMapper.toDTOList(reservations));
    }


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

    @GetMapping("/sold")
    public ResponseEntity<List<ReservationDTO>> getSoldReservations() {
        List<Reservation> soldReservations = reservationService.getSoldReservations();
        return ResponseEntity.ok(reservationMapper.toDTOList(soldReservations));
    }

}