package com.cgi.privsense.piidetector.api;

import com.cgi.privsense.piidetector.model.ColumnPIIInfo;
import com.cgi.privsense.piidetector.model.TablePIIInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for managing the cache of PII detection results.
 * Improves performance by avoiding redundant processing of the same data.
 */
public interface PIIDetectionCacheManager {
    
    /**
     * Stores table PII detection results in the cache.
     * 
     * @param tableName Name of the table
     * @param hashKey Hash of the data that was analyzed
     * @param info PII detection results
     */
    void cacheTableResults(String tableName, String hashKey, TablePIIInfo info);
    
    /**
     * Looks up table PII detection results in the cache.
     * 
     * @param tableName Name of the table
     * @param hashKey Hash of the data to analyze
     * @return Optional containing cached results if found
     */
    Optional<TablePIIInfo> getTableResults(String tableName, String hashKey);
    
    /**
     * Stores column PII detection results in the cache.
     * 
     * @param tableName Name of the table
     * @param columnName Name of the column
     * @param hashKey Hash of the data that was analyzed
     * @param info PII detection results
     */
    void cacheColumnResults(String tableName, String columnName, String hashKey, ColumnPIIInfo info);
    
    /**
     * Looks up column PII detection results in the cache.
     * 
     * @param tableName Name of the table
     * @param columnName Name of the column
     * @param hashKey Hash of the data to analyze
     * @return Optional containing cached results if found
     */
    Optional<ColumnPIIInfo> getColumnResults(String tableName, String columnName, String hashKey);
    
    /**
     * Generates a hash key for column data.
     * Used to determine if the same data has been processed before.
     * 
     * @param samples List of data samples
     * @return Hash string
     */
    String generateHashKey(List<String> samples);
    
    /**
     * Generates a hash key for multiple columns.
     * Used to determine if the same table data has been processed before.
     * 
     * @param columnData Map of column names to data samples
     * @return Hash string
     */
    String generateHashKey(Map<String, List<String>> columnData);
    
    /**
     * Clears the entire cache.
     */
    void clearCache();
    
    /**
     * Gets statistics about cache usage.
     * 
     * @return Map with statistics like hit rate, size, etc.
     */
    Map<String, Object> getCacheStats();
}