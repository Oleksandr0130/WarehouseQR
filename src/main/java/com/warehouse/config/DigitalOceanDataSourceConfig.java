package com.warehouse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Profile("fake")
@Configuration
public class DigitalOceanDataSourceConfig {

    @Value("${DB_USERNAME}")
    private String username;

    @Value("${DB_PASSWORD}")
    private String password;

    @Value("${DB_HOST}")
    private String host;

    @Value("${DB_PORT}")
    private String port;

    @Value("${DB_NAME}")
    private String dbName;

    @Bean
    public DataSource dataSource() {
        return DataSourceBuilder.create()
                .driverClassName("org.postgresql.Driver")
                .url("jdbc:postgresql://" + host + ":" + port + "/" + dbName)
//                .url(String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName )
                .username(username)
                .password(password)
                .build();
    }
}
