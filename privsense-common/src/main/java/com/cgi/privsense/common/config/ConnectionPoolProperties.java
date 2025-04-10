package com.cgi.privsense.common.config;

import lombok.Data;

/**
 * Connection pool configuration properties.
 */
@Data
public class ConnectionPoolProperties {
    /**
     * Default maximum pool size.
     */
    private int defaultMaxPoolSize = 10;

    /**
     * Default minimum idle connections.
     */
    private int defaultMinIdle = 2;

    /**
     * Default connection timeout in milliseconds.
     */
    private int defaultConnectionTimeout = 30000;

    /**
     * Default idle timeout in milliseconds.
     */
    private int defaultIdleTimeout = 60000;

    /**
     * Default maximum lifetime in milliseconds.
     */
    private int defaultMaxLifetime = 1800000;

    /**
     * Default leak detection threshold in milliseconds.
     */
    private int defaultLeakDetectionThreshold = 60000;
}