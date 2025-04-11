package com.cgi.privsense.piidetector.api;

import com.cgi.privsense.piidetector.model.ColumnPIIInfo;

/**
 * Interface for enhancing PII detection confidence based on contextual information.
 * Uses surrounding table and column context to improve detection accuracy.
 */
public interface PIIContextEnhancer {
    
    /**
     * Enhances the confidence of detected PII types based on column context.
     * Uses information such as column name, other columns in the table, and data patterns.
     * 
     * @param tableName Table name
     * @param columnName Column name
     * @param columnInfo Information about PII detected in the column
     */
    void enhanceConfidenceWithContext(String tableName, String columnName, ColumnPIIInfo columnInfo);
    
    /**
     * Analyzes relationships between columns in a table to enhance PII detection.
     * For example, detects when a first_name column is next to a last_name column.
     * 
     * @param tableName Table name
     * @param columnInfos Array of column PII info objects
     */
    void analyzeColumnRelationships(String tableName, ColumnPIIInfo[] columnInfos);
    
    /**
     * Determines if a column name strongly indicates a specific PII type.
     * 
     * @param columnName Column name to analyze
     * @return The most likely PII type name, or null if no strong indication
     */
    String getStrongPIIIndicatorFromName(String columnName);
}