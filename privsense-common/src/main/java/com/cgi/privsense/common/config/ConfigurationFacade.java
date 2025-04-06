package com.cgi.privsense.common.config;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Facade for accessing application configuration properties.
 * Provides convenient access methods for various parts of the application.
 */
@Component
public class ConfigurationFacade {
    private final GlobalProperties globalProperties;

    public ConfigurationFacade(GlobalProperties globalProperties) {
        this.globalProperties = globalProperties;
    }

    /**
     * Gets the database scanner properties.
     *
     * @return Database scanner properties
     */
    public DatabaseScannerProperties getDbScanner() {
        return globalProperties.getDbScanner();
    }

    /**
     * Gets the PII detector properties.
     *
     * @return PII detector properties
     */
    public PiiDetectorProperties getPiiDetector() {
        return globalProperties.getPiiDetector();
    }

    /**
     * Gets the NER service URL.
     *
     * @return NER service URL
     */
    public String getNerServiceUrl() {
        return globalProperties.getPiiDetector().getNer().getServiceUrl();
    }

    /**
     * Gets whether to trust all certificates for NER service.
     *
     * @return Whether to trust all certificates
     */
    public boolean getNerTrustAllCerts() {
        return globalProperties.getPiiDetector().getNer().getTrustAllCerts();
    }

    /**
     * Gets database scanner configuration as a map for backward compatibility.
     *
     * @return A map with references to all database scanner config objects
     */
    public Map<String, Object> getDbScannerAsMap() {
        Map<String, Object> dbScanner = new HashMap<>();
        dbScanner.put("drivers", globalProperties.getDbScanner().getDrivers());
        dbScanner.put("queue", globalProperties.getDbScanner().getQueue());
        dbScanner.put("threads", globalProperties.getDbScanner().getThreads());
        dbScanner.put("sampling", globalProperties.getDbScanner().getSampling());
        return dbScanner;
    }
}