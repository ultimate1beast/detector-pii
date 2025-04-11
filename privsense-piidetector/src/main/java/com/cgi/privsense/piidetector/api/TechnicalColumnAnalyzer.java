package com.cgi.privsense.piidetector.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for analyzing and identifying technical columns that are unlikely to contain PII.
 * Technical columns include IDs, timestamps, flags, and other non-personal data.
 */
public interface TechnicalColumnAnalyzer {
    
    /**
     * Analyzes column names to identify likely technical columns.
     * 
     * @param columnNames List of column names to analyze
     * @return Set of column names identified as technical
     */
    Set<String> identifyTechnicalColumns(List<String> columnNames);
    
    /**
     * Evaluates if a specific column name indicates a technical column.
     * 
     * @param columnName Column name to evaluate
     * @return true if the column is likely technical
     */
    boolean isTechnicalColumn(String columnName);
    
    /**
     * Analyzes both column names and data samples to identify technical columns.
     * This provides higher confidence identification than name analysis alone.
     * 
     * @param columnData Map of column names to sample data
     * @return Set of column names identified as technical
     */
    Set<String> identifyTechnicalColumnsWithData(Map<String, List<String>> columnData);
    
    /**
     * Gets the confidence level that a column is technical based on name and/or data.
     * 
     * @param columnName Column name
     * @param samples Optional data samples (can be null)
     * @return Confidence score from 0.0 to 1.0
     */
    double getTechnicalConfidence(String columnName, List<String> samples);
}