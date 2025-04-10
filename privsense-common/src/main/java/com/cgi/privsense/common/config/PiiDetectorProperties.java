package com.cgi.privsense.common.config;

import lombok.Data;


/**
 * PII detector configuration properties.
 */
@Data
public class PiiDetectorProperties {
    /**
     * Confidence configuration.
     */
    private ConfidenceProperties confidence = new ConfidenceProperties();

    /**
     * Sampling configuration.
     */
    private PiiSamplingProperties sampling = new PiiSamplingProperties();

    /**
     * NER configuration.
     */
    private NerProperties ner = new NerProperties();
}