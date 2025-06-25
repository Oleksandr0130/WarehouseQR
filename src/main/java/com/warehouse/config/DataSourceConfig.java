package com.warehouse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class DataSourceConfig {
    @Bean
    public DynamicDataSource dynamicDataSource() {
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        dynamicDataSource.setTargetDataSources(new HashMap<>()); // Устанавливаем пустой mapping
        dynamicDataSource.afterPropertiesSet(); // Применяем изменения
        return dynamicDataSource;
    }

}
