package com.cgi.privsense.common.config;

import lombok.Data;

/**
 * Database scanner configuration properties.
 */
@Data
public class DatabaseScannerProperties {
    /**
     * Driver configuration.
     */
    private DriverProperties drivers = new DriverProperties();

    /**
     * Queue configuration.
     */
    private QueueProperties queue = new QueueProperties();

    /**
     * Thread configuration.
     */
    private ThreadProperties threads = new ThreadProperties();

    /**
     * Sampling configuration.
     */
    private SamplingProperties sampling = new SamplingProperties();

    /**
     * Connection pool configuration.
     */
    private ConnectionPoolProperties connectionPool = new ConnectionPoolProperties();
}