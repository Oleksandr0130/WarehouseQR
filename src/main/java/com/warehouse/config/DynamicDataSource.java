package com.warehouse.config;

import com.warehouse.utils.TenantContext;
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
        return TenantContext.getCurrentTenant();
    }

    public void addDataSource(String tenantId, DataSource dataSource) {
        this.dataSources.put(tenantId, dataSource);
        super.setTargetDataSources(this.dataSources);
        super.afterPropertiesSet(); // Перезагрузить источники данных
    }

}
