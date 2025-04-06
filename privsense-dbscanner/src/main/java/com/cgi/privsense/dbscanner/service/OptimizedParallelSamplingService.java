package com.cgi.privsense.dbscanner.service;

import com.cgi.privsense.common.config.GlobalProperties;
import com.cgi.privsense.common.util.DatabaseUtils;
import com.cgi.privsense.dbscanner.core.datasource.DataSourceProvider;
import com.cgi.privsense.dbscanner.core.scanner.DatabaseScanner;
import com.cgi.privsense.dbscanner.core.scanner.DatabaseScannerFactory;
import com.cgi.privsense.dbscanner.exception.DatabaseOperationException;
import com.cgi.privsense.dbscanner.model.DataSample;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Improved service for parallel sampling of database data.
 * Uses modern Java concurrency features for better performance.
 */
@Slf4j
@Service
public class OptimizedParallelSamplingService {

    /**
     * Provider for data sources.
     */
    private final DataSourceProvider dataSourceProvider;

    /**
     * Factory for database scanners.
     */
    private final DatabaseScannerFactory scannerFactory;

    /**
     * Maximum number of threads in the direct pool.
     */
    private final int maxThreads;

    /**
     * Default timeout for operations.
     */
    private final long defaultTimeout;

    /**
     * Timeout unit.
     */
    private final TimeUnit timeoutUnit;

    /**
     * Executor service for parallel operations.
     */
    private final ExecutorService executorService;

    /**
     * Constructor with GlobalProperties for centralized configuration.
     *
     * @param dataSourceProvider Provider for data sources
     * @param scannerFactory     Factory for database scanners
     * @param properties         Global application properties
     */
    public OptimizedParallelSamplingService(
            DataSourceProvider dataSourceProvider,
            DatabaseScannerFactory scannerFactory,
            GlobalProperties properties) {

        this.dataSourceProvider = dataSourceProvider;
        this.scannerFactory = scannerFactory;

        // Directly access properties from the injected GlobalProperties object
        // using the specific config classes and their getters.
        this.maxThreads = properties.getThreads().getMaxPoolSize(); // Correct access
        this.defaultTimeout = properties.getSampling().getTimeout(); // Correct access
        this.timeoutUnit = properties.getSampling().getTimeoutUnit(); // Correct access

        // Create a thread pool with custom thread factory
        this.executorService = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("sampler-", 0).factory()
        );

        log.info("Initialized parallel sampling service with {} max threads", maxThreads);
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

    /**
     * Samples data from a table by delegating to the appropriate scanner.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableName    Table name
     * @param limit        Maximum number of rows
     * @return Data sample
     */
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

    /**
     * Samples data from a column by delegating to the appropriate scanner.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableName    Table name
     * @param columnName   Column name
     * @param limit        Maximum number of values
     * @return List of sampled values
     */
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

    /**
     * Samples data from multiple columns in parallel using CompletableFuture.
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
        StopWatch watch = new StopWatch();
        watch.start();

        // First, validate the table and columns
        List<String> existingColumns = validateColumnsExistence(dbType, connectionId, tableName, columnNames);

        if (existingColumns.isEmpty()) {
            log.warn("No valid columns found for sampling in table: {}", tableName);
            return new HashMap<>();
        }

        // For a small number of columns, use a single query (more efficient)
        if (existingColumns.size() <= 3) {
            Map<String, List<Object>> result = sampleColumnsWithSingleQuery(dbType, connectionId, tableName, existingColumns, limit);
            watch.stop();
            log.debug("Sampled {} columns with single query in {} ms", existingColumns.size(), watch.getTotalTimeMillis());
            return result;
        } else {
            // Use CompletableFuture for efficient parallélisation
            Map<String, List<Object>> result = sampleColumnsWithCompletableFutures(dbType, connectionId, tableName, existingColumns, limit);
            watch.stop();
            log.debug("Sampled {} columns with parallel futures in {} ms", existingColumns.size(), watch.getTotalTimeMillis());
            return result;
        }
    }

    /**
     * Validates that columns exist in the table.
     * Uses a single metadata query for efficiency.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableName    Table name
     * @param columnNames  List of column names to validate
     * @return List of existing column names
     */
    private List<String> validateColumnsExistence(String dbType, String connectionId,
                                                  String tableName, List<String> columnNames) {
        DatabaseUtils.validateTableName(tableName);

        try {
            // Get scanner and all columns at once
            DatabaseScanner scanner = getScanner(dbType, connectionId);

            // Get all column metadata for the table in a single query
            final Set<String> tableColumnNames = scanner.scanColumns(tableName)
                    .stream()
                    .map(col -> col.getName().toLowerCase())
                    .collect(Collectors.toSet());

            // Filter requested columns to only include ones that exist
            return columnNames.stream()
                    .filter(Objects::nonNull)
                    .filter(col -> !col.trim().isEmpty())
                    .filter(col -> tableColumnNames.contains(col.toLowerCase()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error validating columns: {}", e.getMessage());
            // Fall back to using all requested columns but filter out nulls and empty strings
            return columnNames.stream()
                    .filter(Objects::nonNull)
                    .filter(col -> !col.trim().isEmpty())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Samples data from multiple columns using CompletableFuture for efficient parallelism.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableName    Table name
     * @param columnNames  List of column names
     * @param limit        Maximum number of values per column
     * @return Map of column name to list of sampled values
     */
    private Map<String, List<Object>> sampleColumnsWithCompletableFutures(String dbType, String connectionId,
                                                                          String tableName, List<String> columnNames, int limit) {
        // Get scanner once to avoid repeated lookups
        final DatabaseScanner scanner = getScanner(dbType, connectionId);

        // Limit concurrent tasks based on maxThreads
        int concurrentTasks = Math.min(columnNames.size(), maxThreads);
        Semaphore semaphore = new Semaphore(concurrentTasks);

        // Create a CompletableFuture for each column
        Map<String, CompletableFuture<List<Object>>> futures = new HashMap<>(columnNames.size());

        for (String columnName : columnNames) {
            futures.put(columnName, CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire(); // Limit concurrent executions
                    log.debug("Sampling column {}.{}", tableName, columnName);
                    return scanner.sampleColumnData(tableName, columnName, limit);
                } catch (Exception e) {
                    log.error("Error sampling column {}.{}: {}", tableName, columnName, e.getMessage());
                    return Collections.emptyList();
                } finally {
                    semaphore.release();
                }
            }, executorService));
        }

        // Wait for all futures to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.values().toArray(new CompletableFuture[0])
        );

        try {
            // Wait with timeout
            allFutures.get(defaultTimeout, timeoutUnit);
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for column sampling to complete");
        } catch (Exception e) {
            log.error("Error waiting for column sampling: {}", e.getMessage());
        }

        // Collect results from futures that completed successfully
        Map<String, List<Object>> results = new HashMap<>();
        futures.forEach((columnName, future) -> {
            try {
                List<Object> values = future.getNow(Collections.emptyList());
                if (!values.isEmpty()) {
                    results.put(columnName, values);
                }
            } catch (Exception e) {
                log.error("Error getting result for column {}: {}", columnName, e.getMessage());
            }
        });

        return results;
    }

    /**
     * Samples data from multiple columns with a single query.
     * More efficient for a small number of columns.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableName    Table name
     * @param columnNames  List of column names
     * @param limit        Maximum number of rows
     * @return Map of column name to list of sampled values
     */
    private Map<String, List<Object>> sampleColumnsWithSingleQuery(String dbType, String connectionId,
                                                                   String tableName, List<String> columnNames, int limit) {
        try {
            DatabaseScanner scanner = getScanner(dbType, connectionId);
            DataSample sample = scanner.sampleTableData(tableName, limit);

            if (sample == null || sample.getRows() == null || sample.getRows().isEmpty()) {
                log.warn("No data returned from table: {}", tableName);
                return Collections.emptyMap();
            }

            // Filter to only include requested columns
            Set<String> requestedCols = new HashSet<>(columnNames.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet()));

            // Extract column data from sample
            Map<String, List<Object>> columnData = new HashMap<>();
            for (String column : columnNames) {
                columnData.put(column, new ArrayList<>(sample.getRows().size()));
            }

            // Extract values for each column from the rows
            for (Map<String, Object> row : sample.getRows()) {
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String colName = entry.getKey();
                    // Check both original case and lowercase for flexibility
                    if (columnNames.contains(colName) || requestedCols.contains(colName.toLowerCase())) {
                        List<Object> colValues = columnData.get(colName);
                        if (colValues == null) {
                            // Try to find by case-insensitive match
                            for (String requestedCol : columnNames) {
                                if (requestedCol.equalsIgnoreCase(colName)) {
                                    colValues = columnData.get(requestedCol);
                                    break;
                                }
                            }
                        }
                        if (colValues != null) {
                            colValues.add(entry.getValue());
                        }
                    }
                }
            }

            return columnData;
        } catch (Exception e) {
            log.error("Error during single query sampling: {}", e.getMessage(), e);
            throw DatabaseOperationException.samplingError("Error sampling columns with single query", e);
        }
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
        StopWatch watch = new StopWatch();
        watch.start();

        Map<String, DataSample> result = sampleTablesWithCompletableFutures(dbType, connectionId, tableNames, limit);

        watch.stop();
        log.debug("Sampled {} tables with parallel futures in {} ms", tableNames.size(), watch.getTotalTimeMillis());
        return result;
    }

    /**
     * Samples multiple tables in parallel using CompletableFuture.
     *
     * @param dbType       Database type
     * @param connectionId Connection ID
     * @param tableNames   List of table names
     * @param limit        Maximum number of rows per table
     * @return Map of table name to data sample
     */
    private Map<String, DataSample> sampleTablesWithCompletableFutures(String dbType, String connectionId,
                                                                       List<String> tableNames, int limit) {
        // Get scanner once to avoid repeated lookups
        final DatabaseScanner scanner = getScanner(dbType, connectionId);

        // Limit concurrent tasks based on maxThreads
        int concurrentTasks = Math.min(tableNames.size(), maxThreads);
        Semaphore semaphore = new Semaphore(concurrentTasks);

        // Create a CompletableFuture for each table
        List<CompletableFuture<Map.Entry<String, DataSample>>> futures = new ArrayList<>(tableNames.size());

        for (String tableName : tableNames) {
            // Validate table name before submitting task
            try {
                DatabaseUtils.validateTableName(tableName);

                CompletableFuture<Map.Entry<String, DataSample>> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        semaphore.acquire(); // Limit concurrent executions
                        log.debug("Sampling table {}", tableName);
                        DataSample sample = scanner.sampleTableData(tableName, limit);
                        return Map.entry(tableName, sample);
                    } catch (Exception e) {
                        log.error("Error sampling table {}: {}", tableName, e.getMessage());
                        return null;
                    } finally {
                        semaphore.release();
                    }
                }, executorService);

                futures.add(future);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid table name '{}': {}", tableName, e.getMessage());
            }
        }

        // Wait for all futures to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            // Wait with timeout
            allFutures.get(defaultTimeout, timeoutUnit);
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for table sampling to complete");
        } catch (Exception e) {
            log.error("Error waiting for table sampling: {}", e.getMessage());
        }

        // Collect results from futures that completed successfully
        Map<String, DataSample> results = new HashMap<>();
        for (CompletableFuture<Map.Entry<String, DataSample>> future : futures) {
            try {
                Map.Entry<String, DataSample> entry = future.getNow(null);
                if (entry != null && entry.getValue() != null) {
                    results.put(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                log.error("Error getting table sampling result: {}", e.getMessage());
            }
        }

        return results;
    }

    /**
     * Closes resources when the service is destroyed.
     */
    public void shutdown() {
        log.info("Shutting down sampling service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}