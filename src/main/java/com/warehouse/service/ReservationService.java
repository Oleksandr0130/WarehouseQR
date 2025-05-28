package com.warehouse.service;

import com.warehouse.model.Item;
import com.warehouse.model.Reservation;
import com.warehouse.repository.ItemRepository;
import com.warehouse.repository.ReservationRepository;
import com.warehouse.utils.QRCodeGenerator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ItemRepository itemRepository;

    @Value("${app.reservation-base-url}")
    private String reservationBaseUrl; // Значение из application.yml

    /**
     * Создание резервации
     */
    @Transactional // Обеспечивает транзакционность для работы с LOB
    public Reservation reserveItem(String orderNumber, String itemName, int quantity, String reservationWeek) throws IOException {
        // Поиск товара
        Item item = itemRepository.findByName(itemName).orElseThrow(() ->
                new IllegalArgumentException("Item not found: " + itemName));

        // Проверяем, хватает ли количества на складе
        if (item.getQuantity() < quantity) {
            throw new IllegalStateException("Not enough quantity available for item: " + itemName);
        }

        item.setQuantity(item.getQuantity() - quantity);
        itemRepository.save(item);

        // Создаем резервацию
        Reservation reservation = new Reservation();
        reservation.setOrderNumber(orderNumber);
        reservation.setItemName(itemName);
        reservation.setReservedQuantity(quantity);
        reservation.setReservationWeek(reservationWeek);
        reservation.setStatus("RESERVED");

        // Генерация QR-кода
        try {
            byte[] qrCodeBytes = QRCodeGenerator.generateQRCodeAsBytes(orderNumber); // Генерируем массив байтов
            reservation.setQrCode(qrCodeBytes); // Сохраняем как byte[]
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code for reservation: " + orderNumber, e);
        }

        // Сохраняем резервацию
        return reservationRepository.save(reservation);
    }



    public String getReservationQrUrl(String orderNumber) {
        return reservationBaseUrl + orderNumber + ".png"; // Формирование полного URL
    }



    /**
     * Завершение резервации
     */
//    public boolean completeReservation(Long id) {
//        // Ищем резервацию по ID
//        Reservation reservation = reservationRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Reservation not found with ID: " + id));
//
//        // Проверяем текущий статус. Только "RESERVED" можно завершить
//        if (!"RESERVED".equals(reservation.getStatus())) {
//            throw new IllegalStateException("Only RESERVED reservations can be completed.");
//        }
//
//        // Обновляем статус на COMPLETED
//        reservation.setStatus("COMPLETED");
//        reservationRepository.save(reservation);
//
//        return true; // Операция завершена успешно
//    }

    public boolean completeReservation(Long id) {
        // Ищем резервацию по ID
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found with ID: " + id));

        // Проверяем текущий статус. Только "RESERVED" можно завершить
        if (!"RESERVED".equals(reservation.getStatus())) {
            throw new IllegalStateException("Only RESERVED reservations can be completed.");
        }

        // Обработка товара
        // Находим товар по имени, связанному с резервацией
        Item item = itemRepository.findByName(reservation.getItemName())
                .orElseThrow(() -> new RuntimeException("Item not found: " + reservation.getItemName()));

        // Списываем окончательно зарезервированную продукцию
        if (item.getQuantity() >= reservation.getReservedQuantity()) {
            item.setQuantity(item.getQuantity() - reservation.getReservedQuantity());
        } else {
            throw new IllegalStateException("Insufficient stock to complete the reservation.");
        }
        itemRepository.save(item);

        // Обновляем статус резервации на COMPLETED
        reservation.setStatus("COMPLETED");
        reservationRepository.save(reservation);

        return true; // Операция завершена успешно
    }

    /**
     * Обработка сканирования QR-кода
     */
    @Transactional
    public void handleScannedQRCode(String orderNumber) {
        // Ищем резервацию по номеру
        Reservation reservation = reservationRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + orderNumber));

        // Если статус не "RESERVED", кидаем ошибку
        if (!"RESERVED".equals(reservation.getStatus())) {
            throw new IllegalStateException("Reservation is not available for selling");
        }

        // Обновляем статус резервации
        reservation.setStatus("SOLD");
        reservation.setSaleDate(LocalDateTime.now(ZoneId.systemDefault()));
        reservationRepository.save(reservation);

        // Обновляем статистику в Item
        Item item = itemRepository.findByName(reservation.getItemName())
                .orElseThrow(() -> new RuntimeException("Item not found: " + reservation.getItemName()));
        item.setSold(item.getSold() + reservation.getReservedQuantity()); // Увеличиваем количество проданных
        itemRepository.save(item);

        // Удаляем QR-код
        String qrCodePath = "reservation/" + orderNumber + ".png";
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(qrCodePath));
        } catch (IOException e) {
            throw new RuntimeException("Unable to delete QR Code for order " + orderNumber, e);
        }

    }

    /**
     * Получение всех резерваций
     */
    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll().stream()
                .filter(reservation -> "RESERVED".equals(reservation.getStatus())) // Только активные резервы
                .toList();

    }

    /**
     * Получение резерваций за конкретную неделю
     */
    public List<Reservation> getReservationsByWeek(String reservationWeek) {
        return reservationRepository.findByReservationWeek(reservationWeek);
    }

    public List<Reservation> getReservationsByOrderPrefix(String orderPrefix) {
        return reservationRepository.findByOrderNumberStartingWith(orderPrefix);
    }


    /**
     * Получение зарезервированных товаров за неделю с сортировкой по имени.
     */
    public List<Reservation> getSortedReservationsByWeek(String reservationWeek) {
        return reservationRepository.findByReservationWeekOrderByItemName(reservationWeek);
    }

    /**
     * Удаление резервации и возврат списанного количества на склад
     */
    public Reservation deleteReservation(Long reservationId) {
        // Найти резервацию по ID
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found with ID: " + reservationId));

        // Получаем связанную запись товара
        Item item = itemRepository.findByName(reservation.getItemName())
                .orElseThrow(() -> new RuntimeException("Item not found: " + reservation.getItemName()));

        // Возвращаем зарезервированное количество обратно в склад
        item.setQuantity(item.getQuantity() + reservation.getReservedQuantity());
        itemRepository.save(item);

        // Удаляем резервацию
        reservationRepository.delete(reservation);

        // Возвращаем удаленную резервацию как подтверждение
        return reservation;
    }

    /**
     * Сохранение массива резерваций.
     */
    @Transactional
    public List<Reservation> saveAll(List<Reservation> reservations) {
        return reservationRepository.saveAll(reservations);
    }

    /**
     * Получение всех проданных резерваций
     */
    public List<Reservation> getSoldReservations() {
        return reservationRepository.findAll().stream()
                .filter(reservation -> "SOLD".equals(reservation.getStatus()))
                .toList();
    }

    public Reservation getReservationById(Long id) {
        // Используем ReservationRepository для поиска резервации по ID
        return reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found with ID: " + id));
    }

    public List<Reservation> searchReservationsByItemName(String searchQuery) {
        return reservationRepository.findByItemNameContainingIgnoreCase(searchQuery);
    }

}