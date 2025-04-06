package com.cgi.privsense.common.config;

import lombok.Data;

/**
 * Confidence threshold configuration properties.
 */
@Data
public class ConfidenceProperties {
    /**
     * Detection confidence threshold.
     */
    private double threshold = 0.7;
}