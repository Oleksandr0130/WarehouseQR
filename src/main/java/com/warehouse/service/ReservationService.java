package com.warehouse.service;

import com.warehouse.model.Item;
import com.warehouse.model.Reservation;
import com.warehouse.repository.ItemRepository;
import com.warehouse.repository.ReservationRepository;
import com.warehouse.utils.QRCodeGenerator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ItemRepository itemRepository;

    /**
     * Создание резервации
     */
//    public Reservation reserveItem(String orderNumber, String itemName, int quantity, String reservationWeek) throws IOException {
//        // Поиск товара в базе данных
//        Item item = itemRepository.findByName(itemName).orElseThrow(() ->
//                new IllegalArgumentException("Item not found: " + itemName));
//
//        // Уменьшаем количество товара
//        item.setQuantity(item.getQuantity() - quantity); // Позволяем указывать отрицательные значения
//        itemRepository.save(item);
//
//        // Создаем резервацию
//        Reservation reservation = new Reservation();
//        reservation.setOrderNumber(orderNumber);
//        reservation.setItemName(itemName);
//        reservation.setReservedQuantity(quantity);
//        reservation.setReservationWeek(reservationWeek);
//        reservation.setStatus("RESERVED");
//        reservationRepository.save(reservation);
//
//        // Генерируем QR-код
//        String qrCodePath = "reservation/" + orderNumber + ".png";
//        QRCodeGenerator.generateQRCode(orderNumber, qrCodePath);
//
//        return reservation;
//    }

    public Reservation reserveItem(String orderNumber, String itemName, int quantity, String reservationWeek) throws IOException {
        Item item = itemRepository.findByName(itemName).orElseThrow(() ->
                new IllegalArgumentException("Item not found: " + itemName));

        item.setQuantity(item.getQuantity() - quantity);
        itemRepository.save(item);

        Reservation reservation = new Reservation();
        reservation.setOrderNumber(orderNumber);
        reservation.setItemName(itemName);
        reservation.setReservedQuantity(quantity);
        reservation.setReservationWeek(reservationWeek);
        reservation.setStatus("RESERVED");
        reservationRepository.save(reservation);

        QRCodeGenerator.generateQRCode(orderNumber, ""); // Генерация и загрузка QR-кода
        return reservation;
    }

    public boolean completeReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found with ID: " + id));

        if (!"RESERVED".equals(reservation.getStatus())) {
            throw new IllegalStateException("Only RESERVED reservations can be completed.");
        }

        reservation.setStatus("COMPLETED");
        reservationRepository.save(reservation);

        return true;
    }

    public void handleScannedQRCode(String orderNumber) {
        Reservation reservation = reservationRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + orderNumber));

        if (!"RESERVED".equals(reservation.getStatus())) {
            throw new IllegalStateException("Reservation is not available for selling");
        }

        reservation.setStatus("SOLD");
        reservation.setSaleDate(LocalDateTime.now(ZoneId.systemDefault()));
        reservationRepository.save(reservation);

        Item item = itemRepository.findByName(reservation.getItemName())
                .orElseThrow(() -> new RuntimeException("Item not found: " + reservation.getItemName()));
        item.setSold(item.getSold() + reservation.getReservedQuantity());
        itemRepository.save(item);
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public List<Reservation> getReservationsByWeek(String reservationWeek) {
        return reservationRepository.findByReservationWeek(reservationWeek);
    }

    public List<Reservation> getSortedReservationsByWeek(String reservationWeek) {
        return reservationRepository.findByReservationWeekOrderByItemName(reservationWeek);
    }

    public Reservation deleteReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found with ID: " + reservationId));

        Item item = itemRepository.findByName(reservation.getItemName())
                .orElseThrow(() -> new RuntimeException("Item not found: " + reservation.getItemName()));

        item.setQuantity(item.getQuantity() + reservation.getReservedQuantity());
        itemRepository.save(item);

        reservationRepository.delete(reservation);

        return reservation;
    }

    @Transactional
    public List<Reservation> saveAll(List<Reservation> reservations) {
        return reservationRepository.saveAll(reservations);
    }

    public List<Reservation> getSoldReservations() {
        return reservationRepository.findAll().stream()
                .filter(reservation -> "SOLD".equals(reservation.getStatus()))
                .toList();
    }


}