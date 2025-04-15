package com.cgi.privsense.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Database-related configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "privsense.database")
public class DatabaseProperties {
    private ScannerProperties scanner = new ScannerProperties();
    private DriverProperties drivers = new DriverProperties();
    private ConnectionPoolProperties connectionPool = new ConnectionPoolProperties();
    private SamplingProperties sampling = new SamplingProperties();
    private TaskProperties tasks = new TaskProperties();
    
    @Data
    public static class ScannerProperties {
        private String cacheTtl = "60m";
        private int maxTables = 1000;
        private String schemaFilter = "public";
        private boolean includeViews = true;
    }
    
    @Data
    public static class DriverProperties {
        private String directory = "${user.home}/.privsense/drivers";
        private String repositoryUrl = "https://repo1.maven.org/maven2";
        private Map<String, String> coordinates = new HashMap<>();
    }
    
    @Data
    public static class ConnectionPoolProperties {
        private int maxSize = 10;
        private int minIdle = 2;
        private int connectionTimeout = 30000;
        private int idleTimeout = 60000;
        private int maxLifetime = 1800000;
        private int leakDetectionThreshold = 60000;
    }
    
    @Data
    public static class SamplingProperties {
        private int defaultSize = 100;
        private int maxSize = 10000;
        private int timeout = 30;
        private TimeUnit timeoutUnit = TimeUnit.SECONDS;
        private boolean useReservoirForLargeTables = true;
        private int reservoirThreshold = 10000;
    }
    
    @Data
    public static class TaskProperties {
        private int queueCapacity = 1000;
        private int batchSize = 20;
        private long pollTimeout = 500;
        private TimeUnit pollTimeoutUnit = TimeUnit.MILLISECONDS;
        private int threadPoolSize = 8;
        private int samplerPoolSize = 4;
        private int samplingConsumers = 3;
    }
}