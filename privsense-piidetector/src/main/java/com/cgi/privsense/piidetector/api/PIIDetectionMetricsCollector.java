package com.cgi.privsense.piidetector.api;

import java.util.Map;

/**
 * Interface for collecting metrics about PII detection operations.
 * Tracks performance, accuracy, and usage statistics.
 */
public interface PIIDetectionMetricsCollector {
    
    /**
     * Records the start of a detection operation for performance tracking.
     * 
     * @param operationType Type of operation (e.g., "table_detection", "column_detection")
     * @return Operation ID for tracking
     */
    String startOperation(String operationType);
    
    /**
     * Records the completion of a detection operation.
     * 
     * @param operationId ID of the operation to complete
     * @param metadata Additional information about the operation
     */
    void completeOperation(String operationId, Map<String, Object> metadata);
    
    /**
     * Records a detection failure.
     * 
     * @param operationType Type of operation that failed
     * @param errorType Type of error that occurred
     * @param errorDetails Additional error information
     */
    void recordFailure(String operationType, String errorType, String errorDetails);
    
    /**
     * Records strategy performance metrics.
     * 
     * @param strategyName Name of the strategy
     * @param executionTimeMs Execution time in milliseconds
     * @param confidence Confidence score produced (0-1)
     */
    void recordStrategyMetrics(String strategyName, long executionTimeMs, double confidence);
    
    /**
     * Records that a PII type was detected.
     * 
     * @param piiType Type of PII that was detected
     * @param confidence Confidence score (0-1)
     * @param strategy Strategy that detected it
     */
    void recordDetection(String piiType, double confidence, String strategy);
    
    /**
     * Gets a summary of collected metrics.
     * 
     * @return Map containing metric summaries
     */
    Map<String, Object> getMetricsSummary();
    
    /**
     * Gets detailed detection metrics by PII type.
     * 
     * @return Map of PII types to detection statistics
     */
    Map<String, Map<String, Object>> getDetectionMetrics();
    
    /**
     * Gets performance metrics for each strategy.
     * 
     * @return Map of strategy names to performance statistics
     */
    Map<String, Map<String, Object>> getStrategyPerformance();
    
    /**
     * Resets all metrics to their initial state.
     */
    void resetMetrics();
}