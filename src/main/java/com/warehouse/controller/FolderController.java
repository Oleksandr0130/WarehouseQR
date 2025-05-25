package com.warehouse.controller;

import com.warehouse.model.Item;
import com.warehouse.model.Reservation;
import com.warehouse.repository.ItemRepository;
import com.warehouse.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//@RestController
//@RequestMapping("/folders")
//public class FolderController {
//
//    @GetMapping("/qrcodes")
//    public List<String> listQRCodes() {
//        File folder = new File("./qrcodes");
//        if (folder.exists() && folder.isDirectory()) {
//            File[] files = folder.listFiles();
//            if (files != null) {
//                return Stream.of(files)
//                        .filter(file -> !file.isDirectory())
//                        .map(file -> "/qrcodes/" + file.getName())
//                        .collect(Collectors.toList());
//            }
//        }
//        return List.of();
//    }
//
//    @GetMapping("/reservation")
//    public List<String> listReservations() {
//        File folder = new File("./reservation");
//        if (folder.exists() && folder.isDirectory()) {
//            File[] files = folder.listFiles();
//            if (files != null) {
//                return Stream.of(files)
//                        .filter(file -> !file.isDirectory())
//                        .map(file -> "/reservation/" + file.getName())
//                        .collect(Collectors.toList());
//            }
//        }
//        return List.of();
//    }

@RestController
@RequestMapping("/folders")
@RequiredArgsConstructor
public class FolderController {

    private final ItemRepository itemRepository;
    private final ReservationRepository reservationRepository;

    @GetMapping(value = "/item/{id}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getItemQRCode(@PathVariable String id) {
        Optional<Item> itemOptional = itemRepository.findById(id);
        if (itemOptional.isPresent() && itemOptional.get().getQrCode() != null) {
            return ResponseEntity.ok(itemOptional.get().getQrCode());
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/reservation/{id}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getReservationQRCode(@PathVariable Long id) {
        Optional<Reservation> reservationOptional = reservationRepository.findById(id);
        if (reservationOptional.isPresent() && reservationOptional.get().getQrCode() != null) {
            return ResponseEntity.ok(reservationOptional.get().getQrCode());
        }
        return ResponseEntity.notFound().build();
    }

}