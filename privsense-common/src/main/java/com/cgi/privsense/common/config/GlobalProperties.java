package com.cgi.privsense.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Global properties for the application.
 */
@Data
@Component
@ConfigurationProperties(prefix = "privsense")
public class GlobalProperties {
    private DatabaseScannerProperties dbScanner = new DatabaseScannerProperties();
    private PiiDetectorProperties piiDetector = new PiiDetectorProperties();

    // Backward compatibility methods - add these
    public ThreadProperties getThreads() {
        return dbScanner.getThreads();
    }

    public SamplingProperties getSampling() {
        return dbScanner.getSampling();
    }

    public DriverProperties getDrivers() {
        return dbScanner.getDrivers();
    }

    public QueueProperties getQueue() {
        return dbScanner.getQueue();
    }
}