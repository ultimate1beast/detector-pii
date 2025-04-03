package com.cgi.privsense.dbscanner.core.datasource;

import com.cgi.privsense.common.util.DatabaseUtils;
import com.cgi.privsense.dbscanner.config.RoutingDataSource;
import com.cgi.privsense.dbscanner.config.EmptyDataSource;
import com.cgi.privsense.dbscanner.config.dtoconfig.DatabaseConnectionRequest;
import com.cgi.privsense.dbscanner.core.driver.DriverManager;
import com.cgi.privsense.dbscanner.exception.DatabaseOperationException;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of DataSourceProvider with improved resource management.
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
     * Lock for thread-safe modifications to the dataSources map.
     */
    private final ReentrantReadWriteLock dataSourcesLock = new ReentrantReadWriteLock();

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
     * Creates a new data source from a connection request with improved resource management.
     *
     * @param request The connection request
     * @return The created data source
     */
    @Override
    public DataSource createDataSource(DatabaseConnectionRequest request) {
        log.info("Creating data source: {}", request.getName());

        // Validate request
        if (request == null || request.getName() == null || request.getName().isEmpty()) {
            throw DatabaseOperationException.dataSourceError("Invalid connection request");
        }

        try {
            // Ensure driver is available
            String driverClassName = resolveDriverClassName(request);
            driverManager.ensureDriverAvailable(driverClassName);

            DataSourceBuilder<?> builder = DataSourceBuilder.create();

            // Build URL if not provided
            String url = resolveJdbcUrl(request);

            // Basic configuration
            builder.url(url)
                    .username(request.getUsername())
                    .password(request.getPassword())
                    .driverClassName(driverClassName);

            // Create Hikari data source
            HikariDataSource dataSource = (HikariDataSource) builder.type(HikariDataSource.class).build();

            // Pool configuration with sensible defaults
            configureConnectionPool(dataSource, request);

            // Set validation query based on database type
            setValidationQueryForDbType(dataSource, request.getDbType());

            // Additional properties
            if (request.getProperties() != null) {
                Properties props = new Properties();
                props.putAll(request.getProperties());
                dataSource.setDataSourceProperties(props);
            }

            // Verify connection before returning
            verifyConnection(dataSource);

            // Store database type for future reference
            dataSourceTypes.put(request.getName(), request.getDbType() != null ? request.getDbType() : "mysql");

            log.info("Data source created successfully: {}", request.getName());
            return dataSource;
        } catch (Exception e) {
            log.error("Failed to create data source: {}", request.getName(), e);
            throw DatabaseOperationException.dataSourceError("Failed to create data source: " + request.getName(), e);
        }
    }

    /**
     * Resolves the driver class name from the request.
     *
     * @param request Connection request
     * @return Driver class name
     */
    private String resolveDriverClassName(DatabaseConnectionRequest request) {
        String driverClassName = request.getDriverClassName();
        if (driverClassName == null) {
            String dbType = request.getDbType() != null ? request.getDbType() : "mysql";
            driverClassName = DatabaseUtils.getDriverClassNameForDbType(dbType);
        }
        return driverClassName;
    }

    /**
     * Resolves the JDBC URL from the request.
     *
     * @param request Connection request
     * @return JDBC URL
     */
    private String resolveJdbcUrl(DatabaseConnectionRequest request) {
        String url = request.getUrl();
        if (url == null) {
            url = DatabaseUtils.buildJdbcUrl(
                    request.getDbType(),
                    request.getHost(),
                    request.getPort(),
                    request.getDatabase()
            );
        }
        return url;
    }

    /**
     * Configures the connection pool parameters.
     *
     * @param dataSource HikariDataSource to configure
     * @param request Connection request
     */
    private void configureConnectionPool(HikariDataSource dataSource, DatabaseConnectionRequest request) {
        dataSource.setMaximumPoolSize(request.getMaxPoolSize() != null ? request.getMaxPoolSize() : 10);
        dataSource.setMinimumIdle(request.getMinIdle() != null ? request.getMinIdle() : 2);
        dataSource.setConnectionTimeout(request.getConnectionTimeout() != null ? request.getConnectionTimeout() : 30000);
        dataSource.setAutoCommit(request.getAutoCommit() != null ? request.getAutoCommit() : true);

        // Add additional pool optimizations
        dataSource.setIdleTimeout(60000); // 1 minute idle timeout
        dataSource.setMaxLifetime(1800000); // 30 minutes max lifetime
        dataSource.setLeakDetectionThreshold(60000); // 1 minute leak detection
    }

    /**
     * Verifies that a connection can be established.
     *
     * @param dataSource DataSource to verify
     * @throws SQLException If connection fails
     */
    private void verifyConnection(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(5)) {
                throw new SQLException("Connection validation failed");
            }
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
     * Registers a data source with thread-safety.
     *
     * @param name Name of the data source
     * @param dataSource The data source
     */
    @Override
    public void registerDataSource(String name, DataSource dataSource) {
        log.info("Registering data source: {}", name);

        dataSourcesLock.writeLock().lock();
        try {
            dataSources.put(name, dataSource);
            updateRoutingDataSource();
        } finally {
            dataSourcesLock.writeLock().unlock();
        }
    }

    /**
     * Updates the routing data source with the current set of data sources.
     * Must be called with write lock held.
     */
    private void updateRoutingDataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>(dataSources);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();
    }

    /**
     * Gets a data source by name with proper ThreadLocal cleanup.
     *
     * @param name Data source name
     * @return The data source
     */
    @Override
    public DataSource getDataSource(String name) {
        dataSourcesLock.readLock().lock();
        try {
            if (!dataSources.containsKey(name)) {
                throw DatabaseOperationException.dataSourceError("DataSource not found: " + name);
            }

            // Set the current data source in ThreadLocal
            RoutingDataSource.setCurrentDataSource(name);
            return routingDataSource;
        } finally {
            dataSourcesLock.readLock().unlock();
        }
    }

    /**
     * Gets a connection from a data source and ensures ThreadLocal cleanup.
     *
     * @param name Data source name
     * @return The connection
     * @throws SQLException If a database access error occurs
     */
    public Connection getConnection(String name) throws SQLException {
        dataSourcesLock.readLock().lock();
        try {
            if (!dataSources.containsKey(name)) {
                throw DatabaseOperationException.dataSourceError("DataSource not found: " + name);
            }

            RoutingDataSource.setCurrentDataSource(name);
            try {
                return routingDataSource.getConnection();
            } finally {
                RoutingDataSource.clearCurrentDataSource();
            }
        } finally {
            dataSourcesLock.readLock().unlock();
        }
    }

    /**
     * Gets the database type for a connection.
     *
     * @param connectionId Connection ID
     * @return Database type
     */
    @Override
    public String getDatabaseType(String connectionId) {
        dataSourcesLock.readLock().lock();
        try {
            if (!dataSources.containsKey(connectionId)) {
                throw new IllegalArgumentException("DataSource not found: " + connectionId);
            }

            // Return stored type if available
            if (dataSourceTypes.containsKey(connectionId)) {
                return dataSourceTypes.get(connectionId);
            }

            // Otherwise, detect the type
            RoutingDataSource.setCurrentDataSource(connectionId);
            try (Connection connection = routingDataSource.getConnection()) {
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
            } finally {
                RoutingDataSource.clearCurrentDataSource();
            }
        } catch (SQLException e) {
            log.error("Failed to determine database type for connection: {}", connectionId, e);
            throw DatabaseOperationException.connectionError("Failed to determine database type for connection: " + connectionId, e);
        } finally {
            dataSourcesLock.readLock().unlock();
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
        dataSourcesLock.readLock().lock();
        try {
            return dataSources.containsKey(name);
        } finally {
            dataSourcesLock.readLock().unlock();
        }
    }

    /**
     * Removes a registered data source with proper resource management.
     *
     * @param name Name of the data source to remove
     * @return true if the data source was removed successfully, false otherwise
     */
    @Override
    public boolean removeDataSource(String name) {
        log.info("Removing data source: {}", name);

        dataSourcesLock.writeLock().lock();
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
        } finally {
            dataSourcesLock.writeLock().unlock();
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

        dataSourcesLock.readLock().lock();
        try {
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
        } finally {
            dataSourcesLock.readLock().unlock();
        }

        return result;
    }
}