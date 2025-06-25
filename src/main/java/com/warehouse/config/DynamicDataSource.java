package com.warehouse.config;

import com.warehouse.utils.TenantContext;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Component
public class DynamicDataSource extends AbstractRoutingDataSource {
    private final Map<Object, Object> dataSources = new HashMap<>();

    @Override
    protected Object determineCurrentLookupKey() {
        // Возвращаем идентификатор текущего арендатора (или default, если не установлен)
        return TenantContext.getCurrentTenant() != null ? TenantContext.getCurrentTenant() : "default";
    }

    public void addDataSource(String tenantId, DataSource dataSource) {
        // Добавляем новый источник данных
        this.dataSources.put(tenantId, dataSource);
        super.setTargetDataSources(this.dataSources);
        super.afterPropertiesSet(); // Применяем изменения
    }

    @Override
    public void afterPropertiesSet() {
        // Если dataSources пустой, добавим дефолтный источник
        if (dataSources.isEmpty()) {
            DataSource defaultDataSource = createDefaultDataSource();
            this.dataSources.put("default", defaultDataSource);
            super.setDefaultTargetDataSource(defaultDataSource);
        }
        super.setTargetDataSources(this.dataSources);
        super.afterPropertiesSet();
    }

    private DataSource createDefaultDataSource() {
        // Создаем дефолтный источник данных
        DriverManagerDataSource defaultDataSource = new DriverManagerDataSource();
        defaultDataSource.setDriverClassName("org.postgresql.Driver");
        defaultDataSource.setUrl("jdbc:postgresql://<DEFAULT_DB_HOST>:<DEFAULT_DB_PORT>/<DEFAULT_DB_NAME>?sslmode=require");
        defaultDataSource.setUsername("<DEFAULT_DB_USER>");
        defaultDataSource.setPassword("<DEFAULT_DB_PASSWORD>");
        return defaultDataSource;
    }
}

