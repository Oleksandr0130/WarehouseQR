package com.warehouse.service;

import com.warehouse.config.DynamicDataSource;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final ConcurrentHashMap<Object, Object> dataSources = new ConcurrentHashMap<>();
    private final DynamicDataSource dynamicDataSource;

    @Value("${spring.datasource.dynamic.host}")
    private String dbHost;

    @Value("${spring.datasource.dynamic.port}")
    private int dbPort;

    @Value("${spring.datasource.dynamic.username}")
    private String dbUsername;

    @Value("${spring.datasource.dynamic.password}")
    private String dbPassword;

    public void addNewCompanyDataSource(String companyName, String dbName) {
        // Создаем новый источник данных через HikariCP
        HikariDataSource newDataSource = new HikariDataSource();
        newDataSource.setJdbcUrl("jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName + "?sslmode=require");
        newDataSource.setUsername(dbUsername);
        newDataSource.setPassword(dbPassword);
        newDataSource.setDriverClassName("org.postgresql.Driver");

        // Добавляем в список источников данных
        dataSources.put(companyName.toLowerCase(), newDataSource);
        dynamicDataSource.addDataSource(companyName.toLowerCase(), newDataSource);
    }
}



