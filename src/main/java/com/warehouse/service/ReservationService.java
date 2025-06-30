package com.warehouse.service;

import com.warehouse.model.Item;
import com.warehouse.model.Reservation;
import com.warehouse.model.User;
import com.warehouse.repository.ItemRepository;
import com.warehouse.repository.ReservationRepository;
import com.warehouse.utils.QRCodeGenerator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ItemRepository itemRepository;

    @Value("${app.reservation-base-url}")
    private String reservationBaseUrl;

    // Получение идентификатора компании текущего пользователя
    private Long getCurrentUserCompanyId() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return currentUser.getCompany().getId();
    }

    /**
     * Получение всех резерваций текущей компании
     */
    public List<Reservation> getAllReservationsForCurrentCompany() {
        Long companyId = getCurrentUserCompanyId();
        return reservationRepository.findAllByCompanyId(companyId);
    }

    /**
     * Получение резерваций за неделю для текущей компании
     */
    public List<Reservation> getReservationsByWeekForCurrentCompany(String reservationWeek) {
        Long companyId = getCurrentUserCompanyId();
        return reservationRepository.findByReservationWeekAndCompanyId(reservationWeek, companyId);
    }

    /**
     * Создание резервации
     */
    @Transactional
    public Reservation reserveItem(String orderNumber, String itemName, int quantity, String reservationWeek) throws IOException {
        Long companyId = getCurrentUserCompanyId();

        // Проверка товара для конкретной компании
        Item item = itemRepository.findByNameAndCompanyId(itemName, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found or not accessible for the current company"));

        // Проверяем наличие достаточного количества товаров на складе
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
        reservation.setCompany(item.getCompany());
        reservation.setStatus("RESERVED");

        // Генерация QR-кода
        byte[] qrCodeBytes = QRCodeGenerator.generateQRCodeAsBytes(orderNumber);
        reservation.setQrCode(qrCodeBytes);

        // Сохраняем резервацию
        return reservationRepository.save(reservation);
    }

    /**
     * Поиск резерваций по названию товара для текущей компании
     */
    public List<Reservation> searchReservationsForCurrentCompany(String itemName) {
        Long companyId = getCurrentUserCompanyId();
        return reservationRepository.searchByItemNameAndCompanyId(itemName, companyId);
    }

    /**
     * Создание URL для скачивания QR-кода резервации
     */
    public String getReservationQrUrl(String orderNumber) {
        return reservationBaseUrl + orderNumber + ".png";
    }

    /**
     * Завершение резервации
     */
    public boolean completeReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found with ID: " + id));

        // Проверка, можно ли завершить резервацию
        if (!"RESERVED".equals(reservation.getStatus())) {
            throw new IllegalStateException("Only RESERVED reservations can be completed.");
        }

        Item item = itemRepository.findByName(reservation.getItemName())
                .orElseThrow(() -> new RuntimeException("Item not found: " + reservation.getItemName()));

        if (item.getQuantity() >= reservation.getReservedQuantity()) {
            item.setQuantity(item.getQuantity() - reservation.getReservedQuantity());
        } else {
            throw new IllegalStateException("Insufficient stock to complete the reservation.");
        }
        itemRepository.save(item);

        reservation.setStatus("COMPLETED");
        reservationRepository.save(reservation);
        return true;
    }

    /**
     * Обработка сканирования QR-кода для продажи
     */
    @Transactional
    public void handleScannedQRCode(String orderNumber) {
        Long companyId = getCurrentUserCompanyId();

        Reservation reservation = reservationRepository.findByOrderNumberAndCompanyId(orderNumber, companyId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + orderNumber));

        if (!"RESERVED".equals(reservation.getStatus())) {
            throw new IllegalStateException("Reservation is not available for selling");
        }

        // Обновляем статус резервации
        reservation.setStatus("SOLD");
        reservation.setSaleDate(LocalDateTime.now(ZoneId.systemDefault()));
        reservationRepository.save(reservation);

        // Обновляем количество проданных товаров
        Item item = itemRepository.findByNameAndCompanyId(reservation.getItemName(), companyId)
                .orElseThrow(() -> new RuntimeException("Item not found: " + reservation.getItemName()));
        item.setSold(item.getSold() + reservation.getReservedQuantity());
        itemRepository.save(item);
    }

    /**
     * Удаляет резервацию и возвращает количество товара на склад
     */
    @Transactional
    public Reservation deleteReservation(Long reservationId) {
        Long companyId = getCurrentUserCompanyId();

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found with ID: " + reservationId));

        if (!reservation.getCompany().getId().equals(companyId)) {
            throw new IllegalStateException("You don't have access to this reservation");
        }

        Item item = itemRepository.findByNameAndCompanyId(reservation.getItemName(), companyId)
                .orElseThrow(() -> new RuntimeException("Item not found: " + reservation.getItemName()));

        item.setQuantity(item.getQuantity() + reservation.getReservedQuantity());
        itemRepository.save(item);

        reservationRepository.delete(reservation);
        return reservation;
    }

    /**
     * Сохранение списка резерваций
     */
    @Transactional
    public List<Reservation> saveAll(List<Reservation> reservations) {
        return reservationRepository.saveAll(reservations);
    }

    /**
     * Получение всех проданных резерваций текущей компании
     */
    public List<Reservation> getSoldReservations() {
        Long companyId = getCurrentUserCompanyId();
        return reservationRepository.findAllByCompanyId(companyId).stream()
                .filter(reservation -> "SOLD".equals(reservation.getStatus()))
                .toList();
    }

    /**
     * Получение резервации по ID
     */
    public Reservation getReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reservation not found with ID: " + id));
    }

    /**
     * Поиск резерваций по названию товара
     */
    public List<Reservation> searchReservationsByItemName(String searchQuery) {
        Long companyId = getCurrentUserCompanyId();
        return reservationRepository.searchByItemNameAndCompanyId(searchQuery, companyId);
    }

    @Transactional
    public List<Reservation> getSortedReservationsByWeek(String reservationWeek) {
        Long companyId = getCurrentUserCompanyId();
        return reservationRepository.findByReservationWeekAndCompanyId(reservationWeek, companyId).stream()
                .sorted(Comparator.comparing(Reservation::getItemName)) // Сортировка по имени товара
                .toList();
    }

    @Transactional
    public List<Reservation> getReservationsByOrderPrefix(String orderPrefix) {
        Long companyId = getCurrentUserCompanyId();
        return reservationRepository.findByOrderNumberStartingWith(orderPrefix, companyId).stream()
                .filter(reservation -> reservation.getCompany().getId().equals(companyId)) // Фильтрация по компании
                .toList();
    }


}