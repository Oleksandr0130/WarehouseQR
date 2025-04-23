package com.warehouse.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.util.Objects;

@Entity
@Data
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderNumber; // Номер заказа в формате "2515303-01-01"
    private String itemName;    // Название зарезервированного товара
    private int reservedQuantity; // Количество зарезервированного товара
    private String reservationWeek; // Например, "KW22"
    private String status;     // Статус резервации ("RESERVED", "SOLD")

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public String getReservationWeek() {
        return reservationWeek;
    }

    public void setReservationWeek(String reservationWeek) {
        this.reservationWeek = reservationWeek;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Reservation that = (Reservation) o;
        return reservedQuantity == that.reservedQuantity && Objects.equals(id, that.id) && Objects.equals(orderNumber, that.orderNumber) && Objects.equals(itemName, that.itemName) && Objects.equals(reservationWeek, that.reservationWeek) && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, orderNumber, itemName, reservedQuantity, reservationWeek, status);
    }
}