package com.cgi.privsense.dbscanner.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import java.util.Optional;

/**
 * A data source that routes to different target data sources based on a lookup key.
 * Uses InheritableThreadLocal to maintain context across thread boundaries.
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    /**
     * ThreadLocal to store the current data source name with inheritance support.
     */
    private static final InheritableThreadLocal<String> currentDataSource = new InheritableThreadLocal<>();
    
    // Default connection ID to use when none is explicitly set
    private static String defaultConnectionId;

    /**
     * Sets the current data source name.
     *
     * @param dataSourceName Data source name
     */
    public static void setCurrentDataSource(String dataSourceName) {
        currentDataSource.set(dataSourceName);
    }
    
    /**
     * Sets the default connection ID to use when none is explicitly set.
     * This helps prevent "No default connection" errors.
     * 
     * @param connectionId The default connection ID
     */
    public static void setDefaultConnectionId(String connectionId) {
        defaultConnectionId = connectionId;
    }

    /**
     * Clears the current data source name.
     */
    public static void clearCurrentDataSource() {
        currentDataSource.remove();
    }

    /**
     * Determines the current lookup key.
     * This is the data source name stored in the ThreadLocal.
     * Falls back to the default connection if none is set.
     *
     * @return The current lookup key
     */
    @Override
    protected Object determineCurrentLookupKey() {
        return Optional.ofNullable(currentDataSource.get()).orElse(defaultConnectionId);
    }
}