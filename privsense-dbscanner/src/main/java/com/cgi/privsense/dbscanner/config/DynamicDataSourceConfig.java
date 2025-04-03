package com.cgi.privsense.dbscanner.config;

import com.cgi.privsense.common.config.GlobalProperties;
import com.cgi.privsense.dbscanner.core.datasource.DataSourceProvider;
import com.cgi.privsense.dbscanner.core.driver.DriverManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Configuration for dynamic data sources.
 * This legacy config just ensures that the beans are properly created,
 * but the actual implementation is moved to DataSourceProviderImpl.
 */
@Slf4j
@Configuration
public class DynamicDataSourceConfig {

    private final DriverManager driverManager;
    private final GlobalProperties properties;

    /**
     * Constructor.
     *
     * @param driverManager The driver dependency manager
     * @param properties Global application properties
     */
    public DynamicDataSourceConfig(DriverManager driverManager, GlobalProperties properties) {
        this.driverManager = driverManager;
        this.properties = properties;
        log.info("Initializing DynamicDataSourceConfig");
    }

    /**
     * Gets the routing data source bean.
     * Delegates to DataSourceProvider implementation.
     *
     * @param dataSourceProvider Data source provider
     * @return The routing data source
     */
    @Bean
    @Primary
    public DataSource routingDataSource(DataSourceProvider dataSourceProvider) {
        // For backward compatibility, return an empty data source initially
        // The actual data sources will be provided by the DataSourceProvider
        return new EmptyDataSource();
    }
}