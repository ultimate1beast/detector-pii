package com.cgi.privsense.dbscanner.service.sampling;

import com.cgi.privsense.common.config.GlobalProperties;
import com.cgi.privsense.dbscanner.core.datasource.DataSourceProvider;
import com.cgi.privsense.dbscanner.core.scanner.DatabaseScanner;
import com.cgi.privsense.dbscanner.core.scanner.DatabaseScannerFactory;
import com.cgi.privsense.dbscanner.exception.DatabaseOperationException;
import com.cgi.privsense.dbscanner.model.DataSample;
import com.cgi.privsense.dbscanner.service.sampling.executor.ParallelExecutionService;
import com.cgi.privsense.dbscanner.service.sampling.util.SamplingDataExtractor;
import com.cgi.privsense.dbscanner.service.sampling.util.SamplingValidationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;

/**
 * Implementation of SamplingStrategy that optimizes for parallel execution of sampling operations.
 */
@Slf4j
@Service
public class OptimizedParallelSamplingStrategy implements SamplingStrategy, DisposableBean {

    private final DataSourceProvider dataSourceProvider;
    private final DatabaseScannerFactory scannerFactory;
    private final ParallelExecutionService executionService;

    /**
     * Constructor with dependencies and configuration.
     *
     * @param dataSourceProvider Provider for data sources
     * @param scannerFactory     Factory for database scanners
     * @param properties         Global application properties
     */
    public OptimizedParallelSamplingStrategy(
            DataSourceProvider dataSourceProvider,
            DatabaseScannerFactory scannerFactory,
            GlobalProperties properties) {

        this.dataSourceProvider = dataSourceProvider;
        this.scannerFactory = scannerFactory;
        
        // Create execution service with configuration from properties
        this.executionService = new ParallelExecutionService(
            properties.getThreads().getMaxPoolSize(),
            properties.getSampling().getTimeout(),
            properties.getSampling().getTimeoutUnit()
        );

        log.info("Initialized optimized parallel sampling strategy");
    }

    /**
     * Gets a database scanner for the specified connection.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @return DatabaseScanner instance
     */
    private DatabaseScanner getScanner(String dbType, String connectionId) {
        DataSource dataSource = dataSourceProvider.getDataSource(connectionId);
        return scannerFactory.getScanner(dbType, dataSource);
    }

    @Override
    public DataSample sampleTable(String dbType, String connectionId, String tableName, int limit) {
        StopWatch watch = new StopWatch();
        watch.start();

        try {
            DatabaseScanner scanner = getScanner(dbType, connectionId);
            DataSample sample = scanner.sampleTableData(tableName, limit);

            watch.stop();
            log.debug("Sampled table {} in {} ms", tableName, watch.getTotalTimeMillis());
            return sample;
        } catch (Exception e) {
            log.error("Error sampling table {}: {}", tableName, e.getMessage(), e);
            throw DatabaseOperationException.samplingError("Error sampling table: " + tableName, e);
        }
    }

    @Override
    public List<Object> sampleColumn(String dbType, String connectionId, String tableName, String columnName, int limit) {
        StopWatch watch = new StopWatch();
        watch.start();

        try {
            DatabaseScanner scanner = getScanner(dbType, connectionId);
            List<Object> values = scanner.sampleColumnData(tableName, columnName, limit);

            watch.stop();
            log.debug("Sampled column {}.{} in {} ms", tableName, columnName, watch.getTotalTimeMillis());
            return values;
        } catch (Exception e) {
            log.error("Error sampling column {}.{}: {}", tableName, columnName, e.getMessage(), e);
            throw DatabaseOperationException.samplingError("Error sampling column: " + tableName + "." + columnName, e);
        }
    }

    @Override
    public Map<String, List<Object>> sampleMultipleColumns(String dbType, String connectionId, 
                                                       String tableName, List<String> columnNames, int limit) {
        StopWatch watch = new StopWatch();
        watch.start();

        // Get the scanner
        final DatabaseScanner scanner = getScanner(dbType, connectionId);
        
        // Validate columns exist
        List<String> existingColumns = SamplingValidationUtils.validateColumnsExistence(
                scanner, tableName, columnNames);

        if (existingColumns.isEmpty()) {
            log.warn("No valid columns found for sampling in table: {}", tableName);
            return new HashMap<>();
        }

        // For a small number of columns, use a single query (more efficient)
        Map<String, List<Object>> result;
        if (existingColumns.size() <= 3) {
            result = sampleColumnsWithSingleQuery(scanner, tableName, existingColumns, limit);
            watch.stop();
            log.debug("Sampled {} columns with single query in {} ms", 
                    existingColumns.size(), watch.getTotalTimeMillis());
        } else {
            // Use parallel execution for multiple columns
            result = sampleColumnsInParallel(scanner, tableName, existingColumns, limit);
            watch.stop();
            log.debug("Sampled {} columns with parallel approach in {} ms", 
                    existingColumns.size(), watch.getTotalTimeMillis());
        }
        
        return result;
    }

    /**
     * Samples data from multiple columns with a single query.
     * More efficient for a small number of columns.
     *
     * @param scanner      Database scanner to use
     * @param tableName    Table name
     * @param columnNames  List of column names
     * @param limit        Maximum number of rows
     * @return Map of column name to list of sampled values
     */
    private Map<String, List<Object>> sampleColumnsWithSingleQuery(DatabaseScanner scanner, 
                                                                String tableName, 
                                                                List<String> columnNames, 
                                                                int limit) {
        try {
            DataSample sample = scanner.sampleTableData(tableName, limit);
            
            if (sample == null || sample.getRows() == null || sample.getRows().isEmpty()) {
                log.warn("No data returned from table: {}", tableName);
                return Collections.emptyMap();
            }

            return SamplingDataExtractor.extractColumnDataFromSample(columnNames, sample);
        } catch (Exception e) {
            log.error("Error during single query sampling: {}", e.getMessage(), e);
            throw DatabaseOperationException.samplingError("Error sampling columns with single query", e);
        }
    }

    /**
     * Samples data from multiple columns in parallel.
     *
     * @param scanner      Database scanner to use
     * @param tableName    Table name
     * @param columnNames  List of column names
     * @param limit        Maximum number of rows per column
     * @return Map of column name to list of sampled values
     */
    private Map<String, List<Object>> sampleColumnsInParallel(DatabaseScanner scanner, 
                                                      String tableName, 
                                                      List<String> columnNames, 
                                                      int limit) {
        // Create a map of column names to tasks
        Map<String, Callable<List<Object>>> tasks = createColumnSamplingTasks(scanner, tableName, columnNames, limit);
        
        // Execute tasks in parallel and collect results
        return executeColumnSamplingTasks(tasks, tableName);
    }
    
    /**
     * Creates tasks for sampling columns.
     *
     * @param scanner      Database scanner to use
     * @param tableName    Table name
     * @param columnNames  List of column names
     * @param limit        Maximum number of rows per column
     * @return Map of column names to callable tasks
     */
    private Map<String, Callable<List<Object>>> createColumnSamplingTasks(
            DatabaseScanner scanner, String tableName, List<String> columnNames, int limit) {
        Map<String, Callable<List<Object>>> tasks = HashMap.newHashMap(columnNames.size());
        
        for (String columnName : columnNames) {
            tasks.put(columnName, () -> scanner.sampleColumnData(tableName, columnName, limit));
        }
        
        return tasks;
    }
    
    /**
     * Executes column sampling tasks in parallel.
     *
     * @param tasks      Map of column names to callable tasks
     * @param tableName  Table name for logging
     * @return Map of column name to list of sampled values
     */
    private Map<String, List<Object>> executeColumnSamplingTasks(
            Map<String, Callable<List<Object>>> tasks, String tableName) {
        Map<String, List<Object>> results = HashMap.newHashMap(tasks.size());
        
        for (Map.Entry<String, Callable<List<Object>>> entry : tasks.entrySet()) {
            String columnName = entry.getKey();
            Callable<List<Object>> task = entry.getValue();
            
            try {
                CompletableFuture<List<Object>> future = createSamplingFuture(tableName, columnName, task);
                processColumnSamplingResult(columnName, future, results);
            } catch (Exception e) {
                log.error("Error creating task for column {}: {}", columnName, e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Creates a CompletableFuture for sampling a column.
     *
     * @param tableName   Table name for logging
     * @param columnName  Column name for logging
     * @param task        Sampling task to execute
     * @return CompletableFuture that will execute the sampling task
     */
    private CompletableFuture<List<Object>> createSamplingFuture(
            String tableName, String columnName, Callable<List<Object>> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Sampling column {}.{}", tableName, columnName);
                return task.call();
            } catch (Exception e) {
                log.error("Error sampling column {}.{}: {}", tableName, columnName, e.getMessage());
                return Collections.emptyList();
            }
        });
    }
    
    /**
     * Processes the result of a column sampling operation.
     *
     * @param columnName  Name of the column
     * @param future      Future that will produce the sampling result
     * @param results     Map to collect results
     */
    private void processColumnSamplingResult(
            String columnName, CompletableFuture<List<Object>> future, Map<String, List<Object>> results) {
        try {
            executionService.executeWithSemaphore(() -> {
                try {
                    List<Object> values = future.get();
                    if (!values.isEmpty()) {
                        results.put(columnName, values);
                    }
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Re-interrupt the thread
                    log.error("Interrupted while getting result for column {}: {}", columnName, e.getMessage());
                    return false;
                } catch (Exception e) {
                    log.error("Error getting result for column {}: {}", columnName, e.getMessage());
                    return false;
                }
            }, new Semaphore(1));
        } catch (Exception e) {
            log.error("Error executing task for column {}: {}", columnName, e.getMessage());
        }
    }

    @Override
    public Map<String, DataSample> sampleMultipleTables(String dbType, String connectionId, 
                                                List<String> tableNames, int limit) {
        StopWatch watch = new StopWatch();
        watch.start();

        // Get scanner
        final DatabaseScanner scanner = getScanner(dbType, connectionId);
        
        // Validate table names
        List<String> validatedTables = SamplingValidationUtils.validateTableNames(tableNames);
        
        if (validatedTables.isEmpty()) {
            log.warn("No valid tables found for sampling");
            return Collections.emptyMap();
        }
        
        // Sample tables in parallel
        Map<String, DataSample> results = HashMap.newHashMap(validatedTables.size());
        
        // Create map of table names to futures
        Map<String, CompletableFuture<DataSample>> futures = HashMap.newHashMap(validatedTables.size());
        
        for (String tableName : validatedTables) {
            futures.put(tableName, CompletableFuture.supplyAsync(() -> {
                try {
                    log.debug("Sampling table {}", tableName);
                    return scanner.sampleTableData(tableName, limit);
                } catch (Exception e) {
                    log.error("Error sampling table {}: {}", tableName, e.getMessage());
                    return null;
                }
            }));
        }
        
        // Execute futures with the execution service
        for (Map.Entry<String, CompletableFuture<DataSample>> entry : futures.entrySet()) {
            String tableName = entry.getKey();
            CompletableFuture<DataSample> future = entry.getValue();
            
            try {
                DataSample sample = future.get();
                if (sample != null) {
                    results.put(tableName, sample);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Re-interrupt the thread
                log.error("Interrupted while getting result for table {}: {}", tableName, e.getMessage());
            } catch (Exception e) {
                log.error("Error getting result for table {}: {}", tableName, e.getMessage());
            }
        }
        
        watch.stop();
        log.debug("Sampled {} tables in {} ms", validatedTables.size(), watch.getTotalTimeMillis());
        
        return results;
    }

    /**
     * Disposes resources when the strategy is destroyed.
     */
    @Override
    public void destroy() {
        executionService.shutdown();
    }
}