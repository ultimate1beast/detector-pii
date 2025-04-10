package com.cgi.privsense.dbscanner.service.sampling;

import com.cgi.privsense.dbscanner.model.DataSample;

import java.util.List;
import java.util.Map;

/**
 * Interface defining the core operations for database sampling.
 * Different implementations can provide optimized strategies for different scenarios.
 */
public interface SamplingStrategy {
    
    /**
     * Samples data from a table.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableName    Table name
     * @param limit        Maximum number of rows
     * @return Data sample
     */
    DataSample sampleTable(String dbType, String connectionId, String tableName, int limit);
    
    /**
     * Samples data from a column.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableName    Table name
     * @param columnName   Column name
     * @param limit        Maximum number of values
     * @return List of sampled values
     */
    List<Object> sampleColumn(String dbType, String connectionId, String tableName, String columnName, int limit);
    
    /**
     * Samples data from multiple columns.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableName    Table name
     * @param columnNames  List of column names
     * @param limit        Maximum number of values per column
     * @return Map of column name to list of sampled values
     */
    Map<String, List<Object>> sampleMultipleColumns(String dbType, String connectionId, String tableName, 
                                                 List<String> columnNames, int limit);
    
    /**
     * Samples data from multiple tables.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableNames   List of table names
     * @param limit        Maximum number of rows per table
     * @return Map of table name to data sample
     */
    Map<String, DataSample> sampleMultipleTables(String dbType, String connectionId, List<String> tableNames, int limit);
}