package com.cgi.privsense.dbscanner.service.queue;

import com.cgi.privsense.common.config.GlobalProperties;
import com.cgi.privsense.common.util.DatabaseUtils;
import com.cgi.privsense.dbscanner.core.datasource.DataSourceProvider;
import com.cgi.privsense.dbscanner.exception.DatabaseOperationException;
import com.cgi.privsense.dbscanner.model.DataSample;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processor for sampling tasks.
 * Uses a thread pool to process tasks from the queue.
 */
@Slf4j
@Component
public class SamplingTaskProcessor implements DisposableBean {
    private final SamplingTaskQueue taskQueue;
    private final DataSourceProvider dataSourceProvider;
    private final ExecutorService executorService;
    private final int numThreads;
    private final long pollTimeout;
    private final TimeUnit pollTimeoutUnit;
    private final AtomicInteger processedTaskCount = new AtomicInteger(0);
    private final AtomicInteger failedTaskCount = new AtomicInteger(0);
    private final AtomicInteger activeThreadCount = new AtomicInteger(0);

    /**
     * Constructor with GlobalProperties for centralized configuration.
     *
     * @param taskQueue Task queue
     * @param dataSourceProvider Data source provider
     * @param properties Global application properties
     */
    public SamplingTaskProcessor(
            SamplingTaskQueue taskQueue,
            DataSourceProvider dataSourceProvider,
            GlobalProperties properties) {

        this.taskQueue = taskQueue;
        this.dataSourceProvider = dataSourceProvider;

        // Access configuration properties directly through their getters
        this.numThreads = properties.getThreads().getSamplerPoolSize();
        this.pollTimeout = properties.getQueue().getPollTimeout();
        this.pollTimeoutUnit = properties.getQueue().getPollTimeoutUnit();

        // Create thread pool with custom thread factory
        this.executorService = Executors.newFixedThreadPool(numThreads, r -> {
            Thread t = new Thread(r);
            t.setName("sampler-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        log.info("Sampling task processor initialized with {} threads", numThreads);
    }

    /**
     * Initializes and starts the consumer threads.
     */
    @PostConstruct
    public void init() {
        // Start consumer threads
        for (int i = 0; i < numThreads; i++) {
            executorService.submit(this::processTasksLoop);
        }
        log.info("Started {} consumer threads for database sampling", numThreads);
    }

    /**
     * Main processing loop for consumer threads.
     */
    private void processTasksLoop() {
        activeThreadCount.incrementAndGet();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Take a task from the queue
                    SamplingTask task = taskQueue.takeTask(pollTimeout, pollTimeoutUnit);

                    if (task == null) {
                        // No task available, check if we should continue
                        if (!taskQueue.hasActiveTasks() && executorService.isShutdown()) {
                            // No more tasks and we're shutting down
                            break;
                        }
                        // Otherwise, continue polling
                        continue;
                    }

                    // Process the task
                    processTask(task);
                    processedTaskCount.incrementAndGet();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.debug("Consumer thread interrupted, exiting loop");
                    break;
                } catch (Exception e) {
                    failedTaskCount.incrementAndGet();
                    log.error("Error processing sampling task: {}", e.getMessage(), e);
                    // Continue processing other tasks
                }
            }
        } finally {
            activeThreadCount.decrementAndGet();
            log.debug("Consumer thread exiting, active threads: {}", activeThreadCount.get());
        }
    }

    /**
     * Processes a single sampling task.
     *
     * @param task Task to process
     */
    private void processTask(SamplingTask task) {
        if (task.isTableSamplingTask()) {
            // Process table sampling task
            try {
                DataSample sample = sampleTable(
                        task.getDbType(),
                        task.getConnectionId(),
                        task.getTableName(),
                        task.getLimit());

                // Invoke callback
                if (task.getTableCallback() != null) {
                    task.getTableCallback().accept(sample);
                }

                log.debug("Completed table sampling task: {}", task.getTableName());
            } catch (Exception e) {
                log.error("Error sampling table {}: {}", task.getTableName(), e.getMessage(), e);
                // Call callback with null to prevent hanging
                if (task.getTableCallback() != null) {
                    task.getTableCallback().accept(null);
                }
                throw DatabaseOperationException.samplingError("Error sampling table: " + task.getTableName(), e);
            }
        } else if (task.isColumnSamplingTask()) {
            // Process column sampling task
            try {
                List<Object> samples = sampleColumn(
                        task.getDbType(),
                        task.getConnectionId(),
                        task.getTableName(),
                        task.getColumnName(),
                        task.getLimit());

                // Invoke callback
                if (task.getColumnCallback() != null) {
                    task.getColumnCallback().accept(samples);
                }

                log.debug("Completed column sampling task: {}.{}", task.getTableName(), task.getColumnName());
            } catch (Exception e) {
                log.error("Error sampling column {}.{}: {}",
                        task.getTableName(), task.getColumnName(), e.getMessage(), e);
                // Call callback with empty list to prevent hanging
                if (task.getColumnCallback() != null) {
                    task.getColumnCallback().accept(Collections.emptyList());
                }
                throw DatabaseOperationException.samplingError("Error sampling column: " + task.getTableName() + "." + task.getColumnName(), e);
            }
        } else {
            log.warn("Invalid task type: {}", task);
        }
    }

    /**
     * Samples data from a table.
     *
     * @param dbType Database type
     * @param connectionId Connection ID
     * @param tableName Table name
     * @param limit Maximum number of rows
     * @return Data sample
     */
    private DataSample sampleTable(String dbType, String connectionId, String tableName, int limit) {
        DataSource dataSource = dataSourceProvider.getDataSource(connectionId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM " + DatabaseUtils.escapeIdentifier(tableName, dbType) + " LIMIT ?")) {

            stmt.setInt(1, limit);
            List<Map<String, Object>> rows = new ArrayList<>();

            try (ResultSet rs = stmt.executeQuery()) {
                int columnCount = rs.getMetaData().getColumnCount();

                // Pre-fetch column names for improved performance
                String[] columnNames = new String[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    columnNames[i] = rs.getMetaData().getColumnName(i + 1);
                }

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>(columnCount);
                    for (int i = 0; i < columnCount; i++) {
                        row.put(columnNames[i], rs.getObject(i + 1));
                    }
                    rows.add(row);
                }
            }

            // Utiliser la méthode create() au lieu de fromRows()
            return DataSample.create(tableName, rows);
        } catch (SQLException e) {
            throw DatabaseOperationException.samplingError("Error sampling table: " + tableName, e);
        }
    }

    /**
     * Samples data from a column.
     *
     * @param dbType Database type
     * @param connectionId Connection ID
     * @param tableName Table name
     * @param columnName Column name
     * @param limit Maximum number of values
     * @return List of sampled values
     */
    private List<Object> sampleColumn(String dbType, String connectionId, String tableName, String columnName, int limit) {
        DataSource dataSource = dataSourceProvider.getDataSource(connectionId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT " + DatabaseUtils.escapeIdentifier(columnName, dbType) +
                             " FROM " + DatabaseUtils.escapeIdentifier(tableName, dbType) + " LIMIT ?")) {

            stmt.setInt(1, limit);
            List<Object> values = new ArrayList<>(limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getObject(1));
                }
            }

            return values;
        } catch (SQLException e) {
            throw DatabaseOperationException.samplingError("Error sampling column: " + tableName + "." + columnName, e);
        }
    }

    /**
     * Gets the number of processed tasks.
     *
     * @return Number of processed tasks
     */
    public int getProcessedTaskCount() {
        return processedTaskCount.get();
    }

    /**
     * Gets the number of failed tasks.
     *
     * @return Number of failed tasks
     */
    public int getFailedTaskCount() {
        return failedTaskCount.get();
    }

    /**
     * Gets the number of active threads.
     *
     * @return Number of active threads
     */
    public int getActiveThreadCount() {
        return activeThreadCount.get();
    }

    /**
     * Shuts down the processor.
     */
    public void shutdown() {
        log.info("Shutting down sampling task processor");

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }

        log.info("Sampling task processor shutdown complete");
    }

    /**
     * Disposes the processor.
     * Called by Spring when the bean is destroyed.
     */
    @Override
    public void destroy() {
        shutdown();
    }
}