package com.cgi.privsense.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PII detection-related configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "privsense.pii-detection")
public class PiiDetectionProperties {
    private NerServiceProperties nerService = new NerServiceProperties();
    private DetectionProperties detection = new DetectionProperties();
    
    @Data
    public static class NerServiceProperties {
        private String url = "http://localhost:5000/ner";
        private int timeout = 10000;
        private String maxRequestSize = "100KB";
        private boolean trustAllCerts = false;
        private String backupUrl = "";
        private int maxRetries = 3;
        private long retryDelayMs = 1000;
    }
    
    @Data
    public static class DetectionProperties {
        private double confidenceThreshold = 0.7;
        private int samplingLimit = 10;
        private int batchSize = 20;
        private boolean enableCache = true;
    }
}