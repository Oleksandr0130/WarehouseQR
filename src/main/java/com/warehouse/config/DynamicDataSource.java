package com.warehouse.config;

import com.warehouse.utils.TenantContext;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Component
public class DynamicDataSource extends AbstractRoutingDataSource {
    private final Map<Object, Object> dataSources = new HashMap<>();

    @PostConstruct
    public void init() {
        // Добавляем дефолтный placeholder источник данных (пустой, до реальной загрузки)
        DataSource defaultDataSource = createDefaultPlaceholderDataSource();
        this.dataSources.put("default", defaultDataSource);

        // Устанавливаем дефолтный источник данных
        super.setDefaultTargetDataSource(defaultDataSource);
        super.setTargetDataSources(dataSources);
        super.afterPropertiesSet(); // Применяем изменения
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant() != null ? TenantContext.getCurrentTenant() : "default";
    }

    public void addDataSource(String tenantId, DataSource dataSource) {
        this.dataSources.put(tenantId, dataSource);
        super.setTargetDataSources(this.dataSources);
        super.afterPropertiesSet(); // Перезагрузить источники данных
    }

    private DataSource createDefaultPlaceholderDataSource() {
        // К примеру, пустая заглушка
        return null;
    }
}
