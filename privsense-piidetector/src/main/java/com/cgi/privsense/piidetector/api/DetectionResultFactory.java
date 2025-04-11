package com.cgi.privsense.piidetector.api;

import com.cgi.privsense.piidetector.model.ColumnPIIInfo;
import com.cgi.privsense.piidetector.model.TablePIIInfo;

import java.util.List;
import java.util.Map;

/**
 * Interface for creating standardized PII detection result objects.
 * Ensures consistent result formatting and structure.
 */
public interface DetectionResultFactory {
    
    /**
     * Creates a TablePIIInfo object with detection results for all columns.
     * 
     * @param tableName Name of the table
     * @param columnResults Map of column names to their PII detection results
     * @return Constructed TablePIIInfo
     */
    TablePIIInfo createTableResult(String tableName, Map<String, ColumnPIIInfo> columnResults);
    
    /**
     * Creates a ColumnPIIInfo object with detection results for a single column.
     * 
     * @param columnName Name of the column
     * @param piiTypes Map of PII types to confidence scores
     * @param sampleCount Number of samples analyzed
     * @return Constructed ColumnPIIInfo
     */
    ColumnPIIInfo createColumnResult(String columnName, Map<String, Double> piiTypes, int sampleCount);
    
    /**
     * Creates a ColumnPIIInfo object for a technical column.
     * Technical columns are assumed to not contain PII.
     * 
     * @param columnName Name of the column
     * @param technicalConfidence Confidence that this is a technical column
     * @return Constructed ColumnPIIInfo
     */
    ColumnPIIInfo createTechnicalColumnResult(String columnName, double technicalConfidence);
    
    /**
     * Creates a ColumnPIIInfo object for an error case.
     * 
     * @param columnName Name of the column
     * @param errorMessage Error message explaining the failure
     * @return Constructed ColumnPIIInfo with error information
     */
    ColumnPIIInfo createErrorResult(String columnName, String errorMessage);
    
    /**
     * Combines multiple detection results for the same column into a single result.
     * Used to merge results from different detection strategies.
     * 
     * @param columnName Name of the column
     * @param results List of detection results to combine
     * @return Combined ColumnPIIInfo
     */
    ColumnPIIInfo combineResults(String columnName, List<Map<String, Double>> results);
}