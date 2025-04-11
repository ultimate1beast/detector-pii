/*
 * PIIDetectorImpl.java - Main implementation of the PII detector
 */
package com.cgi.privsense.piidetector.service;

import com.cgi.privsense.common.config.properties.PiiDetectionProperties;
import com.cgi.privsense.dbscanner.model.ColumnMetadata;
import com.cgi.privsense.dbscanner.model.TableMetadata;
import com.cgi.privsense.dbscanner.service.OptimizedParallelSamplingService;
import com.cgi.privsense.dbscanner.service.ScannerService;

import com.cgi.privsense.piidetector.api.PIIDetectionStrategyFactory;
import com.cgi.privsense.piidetector.api.PIIDetector;
import com.cgi.privsense.piidetector.api.TablePIIService;
import com.cgi.privsense.piidetector.exception.PIIDetectionException;
import com.cgi.privsense.piidetector.model.*;
import com.cgi.privsense.piidetector.model.enums.PIIType;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Main implementation of the PII detector.
 * Uses a pipeline approach to sequentially apply detection strategies.
 * Optimized to use the producer-consumer pattern for data sampling.
 */
@Service
public class PIIDetectorImpl implements PIIDetector {
    private static final Logger log = LoggerFactory.getLogger(PIIDetectorImpl.class);

    private final ScannerService scannerService;
    private final OptimizedParallelSamplingService samplingService;
    private final PIIDetectionPipelineCoordinator pipelineCoordinator;
    private final PIIDetectionMetricsCollector metricsCollector;
    private final DetectionResultCleaner resultCleaner;
    private final PIIDetectionCacheManager cacheManager;
    private final DetectionResultFactory resultFactory;
    private final TablePIIService tablePIIService;

    private double confidenceThreshold;
    private int sampleSize;
    private Map<String, Boolean> activeStrategies = new ConcurrentHashMap<>();
    private Map<String, Integer> tableSampleSizes;

    public PIIDetectorImpl(
            ScannerService scannerService,
            OptimizedParallelSamplingService samplingService,
            PIIDetectionStrategyFactory strategyFactory,
            PIIDetectionPipelineCoordinator pipelineCoordinator,
            PIIDetectionMetricsCollector metricsCollector,
            DetectionResultCleaner resultCleaner,
            PIIDetectionCacheManager cacheManager,
            DetectionResultFactory resultFactory,
            TablePIIService tablePIIService,
            PiiDetectionProperties piiDetectionProperties) {

        this.scannerService = scannerService;
        this.samplingService = samplingService;
        this.pipelineCoordinator = pipelineCoordinator;
        this.metricsCollector = metricsCollector;
        this.resultCleaner = resultCleaner;
        this.cacheManager = cacheManager;
        this.resultFactory = resultFactory;
        this.tablePIIService = tablePIIService;

        // Access properties through the PiiDetectionProperties object
        this.confidenceThreshold = piiDetectionProperties.getDetection().getConfidenceThreshold();
        this.sampleSize = piiDetectionProperties.getDetection().getSamplingLimit();

        // Default: enable all strategies
        strategyFactory.getAllStrategies().forEach(strategy -> activeStrategies.put(strategy.getName(), true));

        log.info("PII Detector initialized");
    }

    @PostConstruct
    public void initCaches() {
        // Create and register table sample sizes cache
        tableSampleSizes = cacheManager.createCache("tableSampleSizesCache");

        // Register active strategies cache
        cacheManager.registerCache("activeStrategies", activeStrategies);

        validateConfiguration();
    }

    private void validateConfiguration() {
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw PIIDetectionException.configError("Confidence threshold must be between 0.0 and 1.0");
        }

        if (sampleSize <= 0) {
            throw PIIDetectionException.configError("Sample size must be positive");
        }

        log.info("PIIDetector configuration validated: threshold={}, sampleSize={}",
                confidenceThreshold, sampleSize);
    }

    @Override
    public PIIDetectionResult detectPII(String connectionId, String dbType) {
        // Reset state and prepare
        resetState();
        long startTime = System.currentTimeMillis();

        log.info("Starting PII detection for connection: {}, type: {}", connectionId, dbType);

        // Initialize the result
        PIIDetectionResult result = initializeResult(connectionId, dbType);

        // Get and sort tables by size
        List<TableMetadata> tables = getAndSortTables(connectionId, dbType);

        // Process each table
        for (TableMetadata table : tables) {
            processTable(result, connectionId, dbType, table);
        }

        // Finalize the result
        finalizeResult(result, startTime);

        return result;
    }

    @Override
    public TablePIIInfo detectPIIInTable(String connectionId, String dbType, String tableName) {
        // Delegate to the dedicated table service
        return tablePIIService.detectPIIInTable(connectionId, dbType, tableName);
    }

    @Override
    public ColumnPIIInfo detectPIIInColumn(String connectionId, String dbType, String tableName, String columnName) {
        log.debug("Analyzing column: {}.{}", tableName, columnName);

        // Get column metadata
        ColumnMetadata columnMeta = getColumnMetadata(connectionId, dbType, tableName, columnName);
        if (columnMeta == null) {
            return resultFactory.createEmptyResult(tableName, columnName);
        }

        // Get the appropriate sample size for this table
        int effectiveSampleSize = tableSampleSizes.getOrDefault(tableName, sampleSize);

        // Sample data
        List<Object> sampleData = fetchIndividualColumnSample(dbType, connectionId, tableName, columnName,
                effectiveSampleSize);

        // Process the column
        return processColumn(connectionId, dbType, tableName, columnMeta, sampleData);
    }

    @Override
    public void setConfidenceThreshold(double confidenceThreshold) {
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw PIIDetectionException.configError("Confidence threshold must be between 0.0 and 1.0");
        }
        this.confidenceThreshold = confidenceThreshold;

        // Centralize threshold propagation through pipeline coordinator only
        pipelineCoordinator.setConfidenceThreshold(confidenceThreshold);

        // Clear caches since threshold changed
        clearCaches();
    }

    @Override
    public void configureStrategies(Map<String, Boolean> strategyMap) {
        this.activeStrategies.putAll(strategyMap);
        clearCaches();
    }

    /**
     * Sets the sample size to use for analysis.
     *
     * @param sampleSize Number of records to sample per column
     */
    public void setSampleSize(int sampleSize) {
        if (sampleSize < 1) {
            throw PIIDetectionException.configError("Sample size must be positive");
        }
        this.sampleSize = sampleSize;

        // Reset table-specific sample sizes
        tableSampleSizes.clear();
    }

    /**
     * Sets an adaptive sample size for a specific table.
     *
     * @param tableName Table name
     * @param rowCount  Approximate row count for sizing calculation
     */
    public void setSampleSizeAdaptive(String tableName, int rowCount) {
        int adaptiveSize = Math.clamp(rowCount / 1000, 5, 50);
        tableSampleSizes.put(tableName, adaptiveSize);
    }

    /**
     * Clears all caches in the PII detector.
     */
    @CacheEvict(value = "tableResults", allEntries = true)
    public void clearCaches() {
        // Use the cache manager to clear all caches
        cacheManager.clearAll();
        // Clear pipeline coordinator cache as well
        pipelineCoordinator.clearCache();
        log.info("All PII detector caches cleared");
    }

    /**
     * Sets the self-reference to this bean.
     * Used by CachingConfiguration to ensure that internal calls go through the
     * caching proxy.
     *
     * @param self The proxied PIIDetector (this bean)
     */
    public void setSelf(PIIDetector self) {
        // Using self as a local variable in this method, no need to store as class
        // field
        log.debug("Self-reference to PIIDetector received for proper cache handling");
    }

    /* Private helper methods */

    private void resetState() {
        metricsCollector.resetMetrics();
        clearCaches();
    }

    private PIIDetectionResult initializeResult(String connectionId, String dbType) {
        return PIIDetectionResult.builder()
                .connectionId(connectionId)
                .dbType(dbType)
                .tableResults(new ArrayList<>())
                .piiTypeCounts(new EnumMap<>(PIIType.class))
                .additionalMetadata(new HashMap<>())
                .build();
    }

    private List<TableMetadata> getAndSortTables(String connectionId, String dbType) {
        try {
            List<TableMetadata> tables = scannerService.scanTables(dbType, connectionId);
            log.info("Number of tables found: {}", tables.size());

            // Configure adaptive sample sizes
            configureAdaptiveSampleSizes(connectionId, dbType, tables);

            // Sort tables by size
            List<TableMetadata> sortedTables = new ArrayList<>(tables);
            sortedTables.sort(Comparator.comparingInt(table -> {
                try {
                    return scannerService.scanColumns(dbType, connectionId, table.getName()).size();
                } catch (Exception e) {
                    return Integer.MAX_VALUE;
                }
            }));

            return sortedTables;
        } catch (Exception e) {
            throw new PIIDetectionException("Failed to retrieve tables for detection", e);
        }
    }

    private void processTable(PIIDetectionResult result, String connectionId, String dbType, TableMetadata table) {
        try {
            // Use the dedicated table service - no more direct calls bypassing cache
            TablePIIInfo tableResult = tablePIIService.detectPIIInTable(connectionId, dbType, table.getName());
            result.addTableResult(tableResult);
            log.info("Processed table: {}, PII detected: {}", table.getName(), tableResult.isHasPii());
        } catch (Exception e) {
            log.error("Error processing table {}: {}", table.getName(), e.getMessage(), e);
        }
    }

    private void finalizeResult(PIIDetectionResult result, long startTime) {
        long endTime = System.currentTimeMillis();
        result.setProcessingTimeMs(endTime - startTime);

        log.info("PII detection completed. Total time: {} ms. PIIs detected: {}",
                result.getProcessingTimeMs(), result.getTotalPiiCount());

        // Add metrics
        result.getAdditionalMetadata().put("performanceMetrics", metricsCollector.getMetricsReport());

        // Log metrics
        metricsCollector.logMetricsReport();
    }

    private List<Object> fetchIndividualColumnSample(String dbType, String connectionId, String tableName,
            String columnName, int sampleSize) {
        try {
            return samplingService.sampleColumn(dbType, connectionId, tableName, columnName, sampleSize);
        } catch (Exception e) {
            log.warn("Error sampling column {}.{}: {}", tableName, columnName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private ColumnPIIInfo processColumn(String connectionId, String dbType, String tableName,
            ColumnMetadata column, List<Object> samples) {
        String columnName = column.getName();

        try {
            // Process the column through our new pipeline coordinator instead
            ColumnPIIInfo columnResult = pipelineCoordinator.analyzeColumn(
                    connectionId, dbType, tableName, columnName, samples);

            // Set column type from metadata
            columnResult.setColumnType(column.getType());

            // Ensure additionalInfo is not null
            if (columnResult.getAdditionalInfo() == null) {
                columnResult.setAdditionalInfo(new HashMap<>());
            }

            // Clean results to eliminate redundant detections
            return resultCleaner.cleanDetections(columnResult);
        } catch (Exception e) {
            log.error("Error while analyzing column {}.{}: {}", tableName, columnName, e.getMessage(), e);

            // Return error result
            ColumnPIIInfo errorResult = resultFactory.createEmptyResult(tableName, columnName);
            errorResult.setColumnType(column.getType());
            errorResult.getAdditionalInfo().put("error", e.getMessage());

            return errorResult;
        }
    }

    private ColumnMetadata getColumnMetadata(String connectionId, String dbType, String tableName, String columnName) {
        try {
            List<ColumnMetadata> columns = scannerService.scanColumns(dbType, connectionId, tableName);
            return columns.stream()
                    .filter(col -> col.getName().equals(columnName))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Error getting column metadata for {}.{}: {}", tableName, columnName, e.getMessage());
            return null;
        }
    }

    protected void configureAdaptiveSampleSizes(String connectionId, String dbType, List<TableMetadata> tables) {
        for (TableMetadata table : tables) {
            try {
                int columnCount = scannerService.scanColumns(dbType, connectionId, table.getName()).size();

                // Adjust sample size based on column count
                int adaptiveSampleSize;

                if (columnCount <= 5) {
                    // Small tables: use larger samples
                    adaptiveSampleSize = Math.min(20, sampleSize * 2);
                } else if (columnCount >= 50) {
                    // Very large tables: use minimum samples
                    adaptiveSampleSize = Math.max(5, sampleSize / 2);
                } else {
                    // Medium tables: use standard sample size
                    adaptiveSampleSize = sampleSize;
                }

                tableSampleSizes.put(table.getName(), adaptiveSampleSize);

                log.debug("Configured adaptive sample size for table {}: {} columns, {} samples",
                        table.getName(), columnCount, adaptiveSampleSize);

            } catch (Exception e) {
                log.warn("Error configuring adaptive sample size for table {}: {}",
                        table.getName(), e.getMessage());
                // Fallback to default sample size
                tableSampleSizes.put(table.getName(), sampleSize);
            }
        }
    }

    protected <T> List<List<T>> partitionList(List<T> list, int size) {
        if (list == null || list.isEmpty() || size <= 0) {
            return Collections.emptyList();
        }

        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }

        return partitions;
    }
}