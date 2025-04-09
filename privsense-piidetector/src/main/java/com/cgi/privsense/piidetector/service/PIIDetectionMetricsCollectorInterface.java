package com.cgi.privsense.piidetector.service;

import java.util.Map;

/**
 * Interface for collecting metrics about PII detection process.
 * This interface allows for different implementations for production, testing, etc.
 */
public interface PIIDetectionMetricsCollectorInterface {

    /**
     * Records that a column has been processed, with the time it took.
     *
     * @param processingTimeMs Processing time in milliseconds
     */
    void recordColumnProcessed(long processingTimeMs);

    /**
     * Records that a column has been skipped (e.g., technical column).
     */
    void recordSkippedColumn();

    /**
     * Records the processing time for a specific table.
     *
     * @param tableName Table name
     * @param processingTimeMs Processing time in milliseconds
     */
    void recordTableProcessingTime(String tableName, long processingTimeMs);

    /**
     * Records the processing time for a specific column.
     *
     * @param tableName Table name
     * @param columnName Column name
     * @param processingTimeMs Processing time in milliseconds
     */
    void recordColumnProcessingTime(String tableName, String columnName, long processingTimeMs);

    /**
     * Records the detection time for a specific strategy.
     *
     * @param strategyName Strategy name
     * @param detectionTimeMs Detection time in milliseconds
     */
    void recordDetectionTime(String strategyName, long detectionTimeMs);

    /**
     * Records a detection using the heuristic strategy.
     */
    void recordHeuristicDetection();

    /**
     * Records a detection using the regex strategy.
     */
    void recordRegexDetection();

    /**
     * Records a detection using the NER strategy.
     */
    void recordNerDetection();

    /**
     * Records that the pipeline stopped after heuristic detection.
     */
    void recordHeuristicPipelineStop();

    /**
     * Records that the pipeline stopped after regex detection.
     */
    void recordRegexPipelineStop();

    /**
     * Records that the pipeline stopped after NER detection.
     */
    void recordNerPipelineStop();

    /**
     * Records a detection of a specific PII type.
     *
     * @param piiType PII type name
     */
    void recordPiiTypeDetection(String piiType);

    /**
     * Resets all metrics to their initial state.
     */
    void resetMetrics();

    /**
     * Gets a report of all metrics.
     *
     * @return Map containing all metrics
     */
    Map<String, Object> getMetricsReport();

    /**
     * Logs a report of all metrics at INFO level.
     */
    void logMetricsReport();
}