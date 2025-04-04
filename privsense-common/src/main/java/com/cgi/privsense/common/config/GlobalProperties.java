package com.cgi.privsense.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Global properties for the application.
 * Simplified and flattened structure for easier configuration and maintenance.
 */
@Data
@Component
@ConfigurationProperties(prefix = "privsense")
public class GlobalProperties {

    /**
     * Database scanner driver configuration.
     */
    private DriverConfig drivers = new DriverConfig();

    /**
     * Database scanner queue configuration.
     */
    private QueueConfig queue = new QueueConfig();

    /**
     * Database scanner thread configuration.
     */
    private ThreadConfig threads = new ThreadConfig();

    /**
     * Database sampling configuration.
     */
    private SamplingConfig sampling = new SamplingConfig();

    /**
     * Connection pool configuration.
     */
    private ConnectionPoolConfig connectionPool = new ConnectionPoolConfig();

    /**
     * Driver configuration.
     */
    @Data
    public static class DriverConfig {
        /**
         * Directory where downloaded drivers are stored.
         */
        private String directory = "${user.home}/.dbscanner/drivers";

        /**
         * URL of the Maven repository.
         */
        private String repositoryUrl = "https://repo1.maven.org/maven2";

        /**
         * Map of Maven coordinates for each driver.
         * Format: "com.mysql.cj.jdbc.Driver" -> "mysql:mysql-connector-java:8.0.33"
         */
        private Map<String, String> coordinates = new HashMap<>();
    }

    /**
     * Queue configuration.
     */
    @Data
    public static class QueueConfig {
        /**
         * Capacity of the queue.
         */
        private int capacity = 1000;

        /**
         * Batch size for processing.
         */
        private int batchSize = 20;

        /**
         * Timeout for polling the queue.
         */
        private long pollTimeout = 500;

        /**
         * Timeout unit for polling the queue.
         */
        private TimeUnit pollTimeoutUnit = TimeUnit.MILLISECONDS;
    }

    /**
     * Thread configuration.
     */
    @Data
    public static class ThreadConfig {
        /**
         * Maximum pool size.
         */
        private int maxPoolSize = 8;

        /**
         * Sampler pool size.
         */
        private int samplerPoolSize = 4;

        /**
         * Number of sampling consumers.
         */
        private int samplingConsumers = 3;
    }

    /**
     * Sampling configuration.
     */
    @Data
    public static class SamplingConfig {
        /**
         * Timeout for sampling operations.
         */
        private long timeout = 30;

        /**
         * Timeout unit for sampling operations.
         */
        private TimeUnit timeoutUnit = TimeUnit.SECONDS;

        /**
         * Whether to use the queue for sampling.
         */
        private boolean useQueue = true;

        /**
         * Default sampling size.
         */
        private int defaultSampleSize = 100;

        /**
         * Maximum allowed sample size.
         */
        private int maxSampleSize = 10000;
    }

    /**
     * Connection pool configuration.
     */
    @Data
    public static class ConnectionPoolConfig {
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

    /**
     * Get database scanner configuration.
     * Convenience method for backward compatibility.
     *
     * @return A map with references to all database scanner config objects
     */
    public Map<String, Object> getDbScanner() {
        Map<String, Object> dbScanner = new HashMap<>();
        dbScanner.put("drivers", drivers);
        dbScanner.put("queue", queue);
        dbScanner.put("threads", threads);
        dbScanner.put("sampling", sampling);
        return dbScanner;
    }
}