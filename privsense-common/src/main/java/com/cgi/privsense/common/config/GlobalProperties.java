package com.cgi.privsense.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Global properties for the application.
 * This class centralizes all application properties in one place.
 */
@Data
@Component
@ConfigurationProperties(prefix = "privsense")
public class GlobalProperties {

    /**
     * Database scanner configuration.
     */
    @NestedConfigurationProperty
    private final DbScannerProperties dbScanner = new DbScannerProperties();

    /**
     * Database scanner properties.
     */
    @Data
    public static class DbScannerProperties {
        /**
         * Driver configuration.
         */
        @NestedConfigurationProperty
        private final DriverProperties drivers = new DriverProperties();

        /**
         * Queue configuration.
         */
        @NestedConfigurationProperty
        private final QueueProperties queue = new QueueProperties();

        /**
         * Thread configuration.
         */
        @NestedConfigurationProperty
        private final ThreadProperties threads = new ThreadProperties();

        /**
         * Sampling configuration.
         */
        @NestedConfigurationProperty
        private final SamplingProperties sampling = new SamplingProperties();
    }

    /**
     * Driver configuration properties.
     */
    @Data
    public static class DriverProperties {
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
     * Queue configuration properties.
     */
    @Data
    public static class QueueProperties {
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
     * Thread configuration properties.
     */
    @Data
    public static class ThreadProperties {
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
     * Sampling configuration properties.
     */
    @Data
    public static class SamplingProperties {
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
    }
}