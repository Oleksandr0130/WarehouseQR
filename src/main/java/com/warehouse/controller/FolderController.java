package com.warehouse.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/folders")
public class FolderController {

    @GetMapping("/qrcodes")
    public List<String> listQRCodes() {
        File folder = new File("./qrcodes");
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                return Stream.of(files)
                        .filter(file -> !file.isDirectory())
                        .map(file -> "/qrcodes/" + file.getName())
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }

    @GetMapping("/reservation")
    public List<String> listReservations() {
        File folder = new File("./reservation");
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                return Stream.of(files)
                        .filter(file -> !file.isDirectory())
                        .map(file -> "/reservation/" + file.getName())
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }
}