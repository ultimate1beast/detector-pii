package com.cgi.privsense.dbscanner.core.datasource;

import com.cgi.privsense.common.config.GlobalProperties;
import com.cgi.privsense.common.util.DatabaseUtils;
import com.cgi.privsense.dbscanner.config.RoutingDataSource;
import com.cgi.privsense.dbscanner.config.EmptyDataSource;
import com.cgi.privsense.dbscanner.config.dtoconfig.DatabaseConnectionRequest;
import com.cgi.privsense.dbscanner.core.driver.DriverManager;
import com.cgi.privsense.dbscanner.exception.DataSourceException;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of DataSourceProvider.
 * Manages dynamic data sources for the application.
 */
@Slf4j
@Component
@Primary
public class DataSourceProviderImpl implements DataSourceProvider {

    /**
     * Map of registered data sources.
     */
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

    /**
     * The routing data source that switches between data sources.
     */
    private final RoutingDataSource routingDataSource;

    /**
     * The driver dependency manager.
     */
    private final DriverManager driverManager;

    /**
     * Map of data source types (mysql, postgresql, etc.).
     */
    private final Map<String, String> dataSourceTypes = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param driverManager The driver dependency manager
     */
    public DataSourceProviderImpl(DriverManager driverManager) {
        this.driverManager = driverManager;
        this.routingDataSource = new RoutingDataSource();
        this.routingDataSource.setTargetDataSources(new HashMap<>());
        this.routingDataSource.setDefaultTargetDataSource(new EmptyDataSource());
        this.routingDataSource.afterPropertiesSet();

        log.info("Initialized DataSourceProvider");
    }

    /**
     * Creates a new data source from a connection request.
     *
     * @param request The connection request
     * @return The created data source
     */
    @Override
    public DataSource createDataSource(DatabaseConnectionRequest request) {
        try {
            log.info("Creating data source: {}", request.getName());

            // Ensure driver is available
            String driverClassName = request.getDriverClassName();

            if (driverClassName == null) {
                // Handle the case where dbType might also be null
                String dbType = request.getDbType() != null ? request.getDbType() : "mysql";
                driverClassName = DatabaseUtils.getDriverClassNameForDbType(dbType);
            }
            driverManager.ensureDriverAvailable(driverClassName);

            DataSourceBuilder<?> builder = DataSourceBuilder.create();

            // Build URL if not provided
            String url = request.getUrl();
            if (url == null) {
                url = DatabaseUtils.buildJdbcUrl(
                        request.getDbType(),
                        request.getHost(),
                        request.getPort(),
                        request.getDatabase()
                );
            }

            // Basic configuration
            builder.url(url)
                    .username(request.getUsername())
                    .password(request.getPassword())
                    .driverClassName(driverClassName);

            // Create Hikari data source
            HikariDataSource dataSource = (HikariDataSource) builder.type(HikariDataSource.class).build();

            // Pool configuration with sensible defaults
            dataSource.setMaximumPoolSize(request.getMaxPoolSize() != null ? request.getMaxPoolSize() : 10);
            dataSource.setMinimumIdle(request.getMinIdle() != null ? request.getMinIdle() : 2);
            dataSource.setConnectionTimeout(request.getConnectionTimeout() != null ? request.getConnectionTimeout() : 30000);
            dataSource.setAutoCommit(request.getAutoCommit() != null ? request.getAutoCommit() : true);

            // Set validation query based on database type
            setValidationQueryForDbType(dataSource, request.getDbType());

            // Additional properties
            if (request.getProperties() != null) {
                Properties props = new Properties();
                props.putAll(request.getProperties());
                dataSource.setDataSourceProperties(props);
            }

            // Store database type for future reference
            dataSourceTypes.put(request.getName(), request.getDbType() != null ? request.getDbType() : "mysql");

            log.info("Data source created successfully: {}", request.getName());
            return dataSource;
        } catch (Exception e) {
            log.error("Failed to create data source: {}", request.getName(), e);
            throw new DataSourceException("Failed to create data source: " + request.getName(), e);
        }
    }

    /**
     * Sets the validation query for a data source based on the database type.
     *
     * @param dataSource The HikariDataSource to configure
     * @param dbType The database type
     */
    private void setValidationQueryForDbType(HikariDataSource dataSource, String dbType) {
        String type = (dbType != null) ? dbType.toLowerCase() : "mysql";

        switch (type) {
            case "mysql":
                dataSource.setConnectionTestQuery("SELECT 1");
                break;
            case "postgresql":
                dataSource.setConnectionTestQuery("SELECT 1");
                break;
            case "oracle":
                dataSource.setConnectionTestQuery("SELECT 1 FROM DUAL");
                break;
            case "sqlserver":
                dataSource.setConnectionTestQuery("SELECT 1");
                break;
            default:
                dataSource.setConnectionTestQuery("SELECT 1");
        }
    }

    /**
     * Registers a data source.
     *
     * @param name Name of the data source
     * @param dataSource The data source
     */
    @Override
    public void registerDataSource(String name, DataSource dataSource) {
        log.info("Registering data source: {}", name);
        dataSources.put(name, dataSource);
        updateRoutingDataSource();
    }

    /**
     * Updates the routing data source with the current set of data sources.
     */
    private void updateRoutingDataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>(dataSources);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();
    }

    /**
     * Gets a data source by name.
     *
     * @param name Data source name
     * @return The data source
     */
    @Override
    public DataSource getDataSource(String name) {
        if (!dataSources.containsKey(name)) {
            throw new DataSourceException("DataSource not found: " + name);
        }

        RoutingDataSource.setCurrentDataSource(name);
        return routingDataSource;
    }

    /**
     * Gets the database type for a connection.
     *
     * @param connectionId Connection ID
     * @return Database type
     */
    @Override
    public String getDatabaseType(String connectionId) {
        if (!dataSources.containsKey(connectionId)) {
            throw new IllegalArgumentException("DataSource not found: " + connectionId);
        }

        // Return stored type if available
        if (dataSourceTypes.containsKey(connectionId)) {
            return dataSourceTypes.get(connectionId);
        }

        // Otherwise, detect the type
        try {
            log.debug("Detecting database type for connection: {}", connectionId);
            DataSource dataSource = getDataSource(connectionId);
            try (var connection = dataSource.getConnection()) {
                String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();

                // Convert product name to supported type
                String type;
                if (productName.contains("mysql")) {
                    type = "mysql";
                } else if (productName.contains("postgresql")) {
                    type = "postgresql";
                } else if (productName.contains("oracle")) {
                    type = "oracle";
                } else if (productName.contains("sql server")) {
                    type = "sqlserver";
                } else {
                    throw new IllegalArgumentException("Unsupported database type: " + productName);
                }

                // Store for future use
                dataSourceTypes.put(connectionId, type);
                log.debug("Detected database type for connection {}: {}", connectionId, type);
                return type;
            }
        } catch (Exception e) {
            log.error("Failed to determine database type for connection: {}", connectionId, e);
            throw new RuntimeException("Failed to determine database type for connection: " + connectionId, e);
        } finally {
            RoutingDataSource.clearCurrentDataSource();
        }
    }

    /**
     * Checks if a data source with the given name exists.
     *
     * @param name Data source name
     * @return true if the data source exists, false otherwise
     */
    @Override
    public boolean hasDataSource(String name) {
        return dataSources.containsKey(name);
    }

    /**
     * Removes a registered data source.
     *
     * @param name Name of the data source to remove
     * @return true if the data source was removed successfully, false otherwise
     */
    @Override
    public boolean removeDataSource(String name) {
        log.info("Removing data source: {}", name);
        try {
            // Get the data source before removing it
            DataSource dataSource = dataSources.get(name);

            // If it doesn't exist, we're done
            if (dataSource == null) {
                log.warn("Data source not found: {}", name);
                return false;
            }

            // Remove from the data sources map
            dataSources.remove(name);

            // Also remove from the database type map
            dataSourceTypes.remove(name);

            // Update the routing data source
            updateRoutingDataSource();

            // Close the data source if it's a Hikari data source
            if (dataSource instanceof HikariDataSource) {
                log.info("Closing Hikari connection pool for data source: {}", name);
                ((HikariDataSource) dataSource).close();
            }

            log.info("Data source removed successfully: {}", name);
            return true;
        } catch (Exception e) {
            log.error("Failed to remove data source: {}", name, e);
            return false;
        }
    }

    /**
     * Gets information about all registered data sources.
     *
     * @return List of data source information
     */
    @Override
    public List<Map<String, Object>> getDataSourcesInfo() {
        log.info("Getting information about all data sources");
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            String name = entry.getKey();
            DataSource dataSource = entry.getValue();

            Map<String, Object> info = new HashMap<>();
            info.put("name", name);

            // Add database type if available
            if (dataSourceTypes.containsKey(name)) {
                info.put("dbType", dataSourceTypes.get(name));
            }

            // Add additional information for HikariDataSource
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikari = (HikariDataSource) dataSource;
                info.put("jdbcUrl", hikari.getJdbcUrl());
                info.put("username", hikari.getUsername());
                info.put("maxPoolSize", hikari.getMaximumPoolSize());
                info.put("minIdle", hikari.getMinimumIdle());
                info.put("connectionTimeout", hikari.getConnectionTimeout());
                info.put("autoCommit", hikari.isAutoCommit());

                // Add connection pool metrics
                try {
                    var poolMXBean = hikari.getHikariPoolMXBean();
                    if (poolMXBean != null) {
                        info.put("activeConnections", poolMXBean.getActiveConnections());
                        info.put("idleConnections", poolMXBean.getIdleConnections());
                        info.put("totalConnections", poolMXBean.getTotalConnections());
                        info.put("threadsAwaitingConnection", poolMXBean.getThreadsAwaitingConnection());
                    } else {
                        log.warn("HikariPoolMXBean is null for data source: {}", name);
                        info.put("poolStatus", "initializing");
                    }
                } catch (Exception e) {
                    log.warn("Failed to get pool metrics for data source: {}", name, e);
                }
            }

            result.add(info);
        }

        return result;
    }
}