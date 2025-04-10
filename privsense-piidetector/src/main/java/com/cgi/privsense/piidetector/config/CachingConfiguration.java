package com.cgi.privsense.piidetector.config;

import com.cgi.privsense.piidetector.api.PIIDetector;
import com.cgi.privsense.piidetector.service.PIIDetectorImpl;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration for caching support in PII detector.
 * Sets up necessary references for proper cache handling.
 */
@Configuration
public class CachingConfiguration {

    private final PIIDetector piiDetector;

    public CachingConfiguration(PIIDetector piiDetector) {
        this.piiDetector = piiDetector;
    }

    /**
     * Injects the self-reference to PIIDetectorImpl after all beans are
     * initialized.
     * This ensures that the caching proxy is properly applied to internal method
     * calls.
     */
    @PostConstruct
    public void injectSelfReferences() {
        // Using pattern matching with instanceof to avoid explicit cast
        if (piiDetector instanceof PIIDetectorImpl piiDetectorImpl) {
            piiDetectorImpl.setSelf(piiDetector);
        }
    }
}