package com.cgi.privsense.common.config;

import lombok.Data;

/**
 * PII sampling configuration properties.
 */
@Data
public class PiiSamplingProperties {
    /**
     * Sampling limit for PII detection.
     */
    private int limit = 10;
}