package com.cgi.privsense.piidetector.service;

import com.cgi.privsense.dbscanner.model.ColumnMetadata;
import com.cgi.privsense.dbscanner.service.OptimizedParallelSamplingService;
import com.cgi.privsense.dbscanner.service.ScannerService;
import com.cgi.privsense.piidetector.api.TablePIIService;
import com.cgi.privsense.piidetector.model.ColumnPIIInfo;
import com.cgi.privsense.piidetector.model.TablePIIInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Implementation of the TablePIIService.
 * Handles detection of PII within database tables.
 */
@Service
public class TablePIIServiceImpl implements TablePIIService {
    private static final Logger log = LoggerFactory.getLogger(TablePIIServiceImpl.class);

    private final ScannerService scannerService;
    private final OptimizedParallelSamplingService samplingService;
    private final PIIDetectionPipelineCoordinator pipelineCoordinator;
    private final PIIDetectionMetricsCollector metricsCollector;
    private final DetectionResultCleaner resultCleaner;
    private final PIIDetectionCacheManager cacheManager;

    
    public TablePIIServiceImpl(
            ScannerService scannerService,
            OptimizedParallelSamplingService samplingService,
            PIIDetectionPipelineCoordinator pipelineCoordinator,
            PIIDetectionMetricsCollector metricsCollector,
            DetectionResultCleaner resultCleaner,
            PIIDetectionCacheManager cacheManager) {
        this.scannerService = scannerService;
        this.samplingService = samplingService;
        this.pipelineCoordinator = pipelineCoordinator;
        this.metricsCollector = metricsCollector;
        this.resultCleaner = resultCleaner;
        this.cacheManager = cacheManager;
    }

    @Override
    @Cacheable(value = "tableResults", key = "#connectionId + ':' + #dbType + ':' + #tableName")
    public TablePIIInfo detectPIIInTable(String connectionId, String dbType, String tableName) {
        long tableStartTime = System.currentTimeMillis();
        log.info("Analyzing table: {}", tableName);

        TablePIIInfo tableResult = TablePIIInfo.builder()
                .tableName(tableName)
                .columnResults(new ArrayList<>())
                .build();

        // Get table metadata
        List<ColumnMetadata> columns = scannerService.scanColumns(dbType, connectionId, tableName);

        // Get the appropriate sample size for this table
        int effectiveSampleSize = getSampleSizeForTable(tableName);
        log.debug("Using sample size {} for table {}", effectiveSampleSize, tableName);

        // Pre-fetch batches of sample data for efficiency
        Map<String, List<Object>> columnSamples = fetchColumnSamples(connectionId, dbType, tableName, columns,
                effectiveSampleSize);

        // Process columns in batch sizes to limit memory usage
        List<List<ColumnMetadata>> columnBatches = partitionList(columns, 20);

        for (List<ColumnMetadata> batch : columnBatches) {
            for (ColumnMetadata column : batch) {
                String columnName = column.getName();

                // Get pre-fetched samples or fetch individually if not available
                List<Object> samples = columnSamples.getOrDefault(columnName, null);
                if (samples == null) {
                    samples = fetchIndividualColumnSample(dbType, connectionId, tableName, columnName,
                            effectiveSampleSize);
                }

                // Process the column
                ColumnPIIInfo columnResult = processColumn(connectionId, dbType, tableName, column, samples);
                tableResult.addColumnResult(columnResult);
            }
        }

        log.info("Analysis of table {} completed. Columns containing PII: {}/{}",
                tableName,
                tableResult.getColumnResults().stream().filter(ColumnPIIInfo::isPiiDetected).count(),
                tableResult.getColumnResults().size());

        long tableEndTime = System.currentTimeMillis();
        long tableProcessingTime = tableEndTime - tableStartTime;
        metricsCollector.recordTableProcessingTime(tableName, tableProcessingTime);

        return tableResult;
    }
    
    /**
     * Gets the appropriate sample size for a table from the cache manager.
     */
    private int getSampleSizeForTable(String tableName) {
        Map<String, Integer> tableSampleSizes = cacheManager.getCache("tableSampleSizesCache");
        int defaultSampleSize = 10; // Use a default value if not configured
        return tableSampleSizes.getOrDefault(tableName, defaultSampleSize);
    }
    
    /**
     * Fetches samples for multiple columns in a batch operation.
     */
    private Map<String, List<Object>> fetchColumnSamples(String connectionId, String dbType, String tableName,
            List<ColumnMetadata> columns, int sampleSize) {
        try {
            List<String> columnNames = columns.stream()
                    .map(ColumnMetadata::getName)
                    .toList();

            Map<String, List<Object>> samples = samplingService.sampleColumnsInParallel(
                    dbType, connectionId, tableName, columnNames, sampleSize);

            log.debug("Sampled {} columns from table {}", samples.size(), tableName);
            return samples;
        } catch (Exception e) {
            log.warn("Error while batch sampling table {}: {}", tableName, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Fetches samples for a single column.
     */
    private List<Object> fetchIndividualColumnSample(String dbType, String connectionId, String tableName,
            String columnName, int sampleSize) {
        try {
            return samplingService.sampleColumn(dbType, connectionId, tableName, columnName, sampleSize);
        } catch (Exception e) {
            log.warn("Error sampling column {}.{}: {}", tableName, columnName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Processes a column to detect PIIs.
     */
    private ColumnPIIInfo processColumn(String connectionId, String dbType, String tableName,
            ColumnMetadata column, List<Object> samples) {
        String columnName = column.getName();

        try {
            // Process the column through the pipeline coordinator
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
            ColumnPIIInfo errorResult = createEmptyColumnResult(tableName, columnName, column.getType());
            errorResult.getAdditionalInfo().put("error", e.getMessage());

            return errorResult;
        }
    }
    
    /**
     * Creates an empty column result with error details.
     */
    private ColumnPIIInfo createEmptyColumnResult(String tableName, String columnName, String columnType) {
        return ColumnPIIInfo.builder()
                .columnName(columnName)
                .tableName(tableName)
                .columnType(columnType)
                .piiDetected(false)
                .detections(new ArrayList<>())
                .additionalInfo(new HashMap<>())
                .build();
                
        
    }

    /**
     * Partitions a list into smaller lists of specified size.
     */
    private <T> List<List<T>> partitionList(List<T> list, int size) {
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