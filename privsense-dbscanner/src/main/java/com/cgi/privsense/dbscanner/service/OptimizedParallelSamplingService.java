package com.cgi.privsense.dbscanner.service;
import com.cgi.privsense.common.config.properties.DatabaseProperties;
import com.cgi.privsense.dbscanner.core.datasource.DataSourceProvider;
import com.cgi.privsense.dbscanner.core.scanner.DatabaseScannerFactory;
import com.cgi.privsense.dbscanner.model.DataSample;
import com.cgi.privsense.dbscanner.service.sampling.SamplingStrategy;
import com.cgi.privsense.dbscanner.service.sampling.OptimizedParallelSamplingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


/**
 * Service for parallel sampling of database data.
 * This is a facade that delegates to the SamplingStrategy implementation.
 */
@Slf4j
@Service
public class OptimizedParallelSamplingService implements DisposableBean {

    private final SamplingStrategy samplingStrategy;

    /**
 * Constructor with dependencies and configuration.
 *
 * @param dataSourceProvider Provider for data sources
 * @param scannerFactory     Factory for database scanners
 * @param databaseProperties Database properties
 */
public OptimizedParallelSamplingService(
        DataSourceProvider dataSourceProvider,
        DatabaseScannerFactory scannerFactory,
        DatabaseProperties databaseProperties) {
    
    // Create the strategy implementation directly
    // In a more complex application, this could be injected using Spring's dependency injection
    this.samplingStrategy = new OptimizedParallelSamplingStrategy(
            dataSourceProvider, scannerFactory, databaseProperties);
    
    log.info("Initialized optimized parallel sampling service facade");
}

    /**
     * Samples data from a table.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableName    Table name
     * @param limit        Maximum number of rows
     * @return Data sample
     */
    public DataSample sampleTable(String dbType, String connectionId, String tableName, int limit) {
        return samplingStrategy.sampleTable(dbType, connectionId, tableName, limit);
    }

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
    public List<Object> sampleColumn(String dbType, String connectionId, String tableName, String columnName,
            int limit) {
        return samplingStrategy.sampleColumn(dbType, connectionId, tableName, columnName, limit);
    }

    /**
     * Samples data from multiple columns in parallel.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableName    Table name
     * @param columnNames  List of column names
     * @param limit        Maximum number of values per column
     * @return Map of column name to list of sampled values
     */
    public Map<String, List<Object>> sampleColumnsInParallel(String dbType, String connectionId,
            String tableName, List<String> columnNames, int limit) {
        return samplingStrategy.sampleMultipleColumns(dbType, connectionId, tableName, columnNames, limit);
    }

    /**
     * Samples data from multiple tables in parallel.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableNames   List of table names
     * @param limit        Maximum number of rows per table
     * @return Map of table name to data sample
     */
    public Map<String, DataSample> sampleTablesInParallel(String dbType, String connectionId,
            List<String> tableNames, int limit) {
        return samplingStrategy.sampleMultipleTables(dbType, connectionId, tableNames, limit);
    }

    /**
     * Closes resources when the service is destroyed.
     */
    @Override
    public void destroy() {
        log.info("Shutting down sampling service facade");
        // The strategy will be cleaned up by its own destroy method
    }

    /**
     * Shuts down the service.
     */
    public void shutdown() {
        log.info("Shutting down sampling service");
        // This method is kept for backward compatibility
    }
}