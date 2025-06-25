package com.warehouse.service;

import com.warehouse.config.DynamicDataSource;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final Map<Object, Object> dataSources = new HashMap<>();
    private final DynamicDataSource dynamicDataSource;

    // Извлекаем значения из application.yml с помощью @Value
    @Value("${spring.datasource.dynamic.host}")
    private String dbHost;

    @Value("${spring.datasource.dynamic.port}")
    private int dbPort;

    @Value("${spring.datasource.dynamic.username}")
    private String dbUsername;

    @Value("${spring.datasource.dynamic.password}")
    private String dbPassword;

    // Метод для добавления нового источника данных
    public void addNewCompanyDataSource(String companyName, String dbName) {
        HikariDataSource newDataSource = new HikariDataSource();
        newDataSource.setJdbcUrl("jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName + "?sslmode=require");
        newDataSource.setUsername(dbUsername);
        newDataSource.setPassword(dbPassword);
        newDataSource.setDriverClassName("org.postgresql.Driver");

        // Сохраняем в мап источников данных
        dataSources.put(companyName.toLowerCase(), newDataSource);
        dynamicDataSource.setTargetDataSources(dataSources);

        // Перезагружаем источники данных
        dynamicDataSource.afterPropertiesSet();
    }
}

