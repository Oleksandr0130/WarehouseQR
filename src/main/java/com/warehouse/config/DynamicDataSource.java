package com.warehouse.config;

import com.warehouse.utils.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Component
public class DynamicDataSource extends AbstractRoutingDataSource {

    private final Map<Object, Object> dataSources = new HashMap<>();

    // Подгружаем параметры для дефолтного подключения из application.yml
    @Value("${spring.datasource.url}")
    private String defaultUrl;

    @Value("${spring.datasource.username}")
    private String defaultUsername;

    @Value("${spring.datasource.password}")
    private String defaultPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String defaultDriverClassName;

    @Override
    protected Object determineCurrentLookupKey() {
        // Возвращаем идентификатор текущего арендатора (если не указан - используем "default")
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
        // Если dataSources пуст, добавляем дефолтный источник данных
        if (dataSources.isEmpty()) {
            DataSource defaultDataSource = createDefaultDataSource();
            this.dataSources.put("default", defaultDataSource);
            super.setDefaultTargetDataSource(defaultDataSource);
        }
        super.setTargetDataSources(this.dataSources);
        super.afterPropertiesSet();
    }

    private DataSource createDefaultDataSource() {
        // Создаём дефолтный источник данных с параметрами из application.yml
        DriverManagerDataSource defaultDataSource = new DriverManagerDataSource();
        defaultDataSource.setDriverClassName(defaultDriverClassName);
        defaultDataSource.setUrl(defaultUrl);
        defaultDataSource.setUsername(defaultUsername);
        defaultDataSource.setPassword(defaultPassword);
        return defaultDataSource;
    }
}