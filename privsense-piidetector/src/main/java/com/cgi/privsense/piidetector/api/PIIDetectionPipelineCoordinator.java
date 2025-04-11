package com.cgi.privsense.piidetector.api;

import com.cgi.privsense.piidetector.model.ColumnPIIInfo;
import com.cgi.privsense.piidetector.model.TablePIIInfo;

import java.util.List;
import java.util.Map;

/**
 * Interface for coordinating the PII detection pipeline.
 * Orchestrates the various stages of PII detection, from sample preparation to final reporting.
 */
public interface PIIDetectionPipelineCoordinator {
    
    /**
     * Execute the PII detection pipeline for a table.
     * 
     * @param tableName Name of the table
     * @param columnData Map of column names to sample data
     * @return TablePIIInfo with detection results
     */
    TablePIIInfo executePipeline(String tableName, Map<String, List<String>> columnData);
    
    /**
     * Execute the PII detection pipeline for a single column.
     * 
     * @param tableName Name of the table
     * @param columnName Name of the column
     * @param samples List of data samples from the column
     * @return ColumnPIIInfo with detection results
     */
    ColumnPIIInfo executeColumnPipeline(String tableName, String columnName, List<String> samples);
    
    /**
     * Pre-process data before PII detection.
     * Handles filtering, cleaning, and normalization of samples.
     * 
     * @param columnData Raw column data
     * @return Processed column data
     */
    Map<String, List<String>> preProcessData(Map<String, List<String>> columnData);
    
    /**
     * Apply post-processing to detection results.
     * Enhances results with context, applies thresholds, and finalizes confidence scores.
     * 
     * @param tableName Name of the table
     * @param results Preliminary detection results
     * @return Enhanced detection results
     */
    TablePIIInfo postProcessResults(String tableName, TablePIIInfo results);
    
    /**
     * Checks if emergency mode is active and applies appropriate detection strategies.
     * 
     * @return true if emergency mode is active
     */
    boolean checkEmergencyMode();
}