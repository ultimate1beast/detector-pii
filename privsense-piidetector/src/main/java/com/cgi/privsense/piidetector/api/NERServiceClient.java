package com.cgi.privsense.piidetector.api;

import java.util.List;
import java.util.Map;

/**
 * Interface for NER (Named Entity Recognition) service client.
 * Defines operations for interacting with an external NER service.
 */
public interface NERServiceClient {
    
    /**
     * Batch analysis of text samples with the NER service.
     * Processes multiple columns in a single API call to reduce network overhead.
     *
     * @param columnDataMap Map of column names to text samples
     * @return Map of column names to detected entity types with confidence levels
     */
    Map<String, Map<String, Double>> batchAnalyzeText(Map<String, List<String>> columnDataMap);
    
    /**
     * Analyzes text with the NER service.
     *
     * @param textSamples List of text samples to analyze
     * @return Map of detected entity types with their confidence level
     */
    Map<String, Double> analyzeText(List<String> textSamples);
    
    /**
     * Checks if the NER service is available.
     * Takes circuit breaker state into account.
     *
     * @return true if the service is available
     */
    boolean isServiceAvailable();
    
    /**
     * Clears the NER results cache.
     */
    void clearCache();
}