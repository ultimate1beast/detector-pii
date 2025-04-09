package com.cgi.privsense.piidetector.service;

import com.cgi.privsense.common.util.DatabaseUtils;
import com.cgi.privsense.piidetector.config.PipelineConfiguration;
import com.cgi.privsense.piidetector.exception.PIIDetectionException;
import com.cgi.privsense.piidetector.model.ColumnPIIInfo;
import com.cgi.privsense.piidetector.model.PIITypeDetection;
import com.cgi.privsense.piidetector.api.PIIDetectionStrategy;
import com.cgi.privsense.piidetector.strategy.HeuristicNameStrategy;
import com.cgi.privsense.piidetector.strategy.NERModelStrategy;
import com.cgi.privsense.piidetector.strategy.RegexPatternStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Coordinates the PII detection pipeline by orchestrating different detection
 * strategies.
 * This service is responsible for managing the sequence of strategy execution
 * and combines their results. It delegates to specialized services for specific
 * tasks.
 */
@Service
public class PIIDetectionPipelineCoordinator {
    private static final Logger log = LoggerFactory.getLogger(PIIDetectionPipelineCoordinator.class);

    // Constants for strategy names
    private static final String STRATEGY_HEURISTIC = "heuristic";
    private static final String STRATEGY_REGEX = "regex";
    private static final String STRATEGY_NER = "ner";

    // Constants for cache keys
    private static final String COLUMN_RESULT_CACHE = "columnResultCache";

    // Map of strategies by name for easy access
    private final Map<String, PIIDetectionStrategy> strategies = new HashMap<>();

    private final NERModelStrategy nerStrategy;
    private final PIIDetectionMetricsCollectorInterface metricsCollector;
    private final PIIDetectionCacheManager cacheManager;
    private final DetectionResultFactory resultFactory;
    private final StrategyHealthMonitor healthMonitor;
    private final TechnicalColumnAnalyzer technicalColumnAnalyzer;
    private final PIIContextEnhancer contextEnhancer;
    private final SampleFilterService sampleFilterService;
    private final PipelineConfiguration pipelineConfig;

    private boolean earlyTerminationEnabled;
    private boolean cachingEnabled;
    private boolean contextEnhancementEnabled;

    /**
     * Class to hold pipeline execution context parameters to reduce method
     * parameter count.
     */
    private static class PipelineContext {
        final String connectionId;
        final String dbType;
        final String tableName;
        final String columnName;
        final List<Object> sampleData;
        final ColumnPIIInfo columnInfo;
        final long startTime;
        final boolean hasSamples;

        PipelineContext(String connectionId, String dbType, String tableName, String columnName,
                List<Object> sampleData, ColumnPIIInfo columnInfo, long startTime) {
            this.connectionId = connectionId;
            this.dbType = dbType;
            this.tableName = tableName;
            this.columnName = columnName;
            this.sampleData = sampleData;
            this.columnInfo = columnInfo;
            this.startTime = startTime;
            this.hasSamples = sampleData != null && !sampleData.isEmpty();
        }
    }

    public PIIDetectionPipelineCoordinator(
            HeuristicNameStrategy heuristicStrategy,
            RegexPatternStrategy regexStrategy,
            NERModelStrategy nerStrategy,
            PIIDetectionMetricsCollectorInterface metricsCollector,
            PIIDetectionCacheManager cacheManager,
            DetectionResultFactory resultFactory,
            StrategyHealthMonitor healthMonitor,
            TechnicalColumnAnalyzer technicalColumnAnalyzer,
            PIIContextEnhancer contextEnhancer,
            SampleFilterService sampleFilterService,
            PipelineConfiguration pipelineConfig,
            @Value("${piidetector.confidence.threshold:0.7}") double confidenceThreshold) {

        this.nerStrategy = nerStrategy;
        this.metricsCollector = metricsCollector;
        this.cacheManager = cacheManager;
        this.resultFactory = resultFactory;
        this.healthMonitor = healthMonitor;
        this.technicalColumnAnalyzer = technicalColumnAnalyzer;
        this.contextEnhancer = contextEnhancer;
        this.sampleFilterService = sampleFilterService;
        this.pipelineConfig = pipelineConfig;

        // Register strategies by name for easy lookup
        strategies.put(STRATEGY_HEURISTIC, heuristicStrategy);
        strategies.put(STRATEGY_REGEX, regexStrategy);
        strategies.put(STRATEGY_NER, nerStrategy);

        // Initialize strategy health monitor
        healthMonitor.initializeStrategyHealth(strategies.keySet().toArray(new String[0]));

        // Apply standard configuration to all strategies
        this.setConfidenceThreshold(confidenceThreshold);

        // Read configuration flags
        this.earlyTerminationEnabled = pipelineConfig.isFeatureEnabled("earlyTermination");
        this.cachingEnabled = pipelineConfig.isFeatureEnabled("caching");
        this.contextEnhancementEnabled = pipelineConfig.isFeatureEnabled("contextEnhancement");

        log.info("PII detection pipeline coordinator initialized with confidence threshold: {}", confidenceThreshold);
    }

    /**
     * Analyzes a column by applying strategies sequentially.
     * Stops as soon as a detection method gives a result with sufficient
     * confidence.
     *
     * @param connectionId Connection identifier
     * @param dbType       Database type
     * @param tableName    Table name
     * @param columnName   Column name
     * @param sampleData   Data samples for this column
     * @return Information about PIIs detected in the column
     */
    public ColumnPIIInfo analyzeColumn(String connectionId, String dbType, String tableName,
            String columnName, List<Object> sampleData) {
        log.debug("Starting detection pipeline for column {}.{}", tableName, columnName);

        // Validate table and column names
        try {
            validateNames(tableName, columnName);
        } catch (IllegalArgumentException e) {
            throw PIIDetectionException.configError("Invalid table or column name: " + e.getMessage());
        }

        // Check cache first if caching is enabled
        ColumnPIIInfo cachedResult = checkCache(connectionId, dbType, tableName, columnName);
        if (cachedResult != null) {
            return cachedResult;
        }

        // Skip technical columns
        if (technicalColumnAnalyzer.shouldSkipColumn(columnName)) {
            log.debug("Skipping technical column {}.{}", tableName, columnName);
            metricsCollector.recordSkippedColumn();
            ColumnPIIInfo emptyResult = resultFactory.createEmptyResult(tableName, columnName);
            cacheResult(connectionId, dbType, tableName, columnName, emptyResult);
            return emptyResult;
        }

        long startTime = System.currentTimeMillis();
        ColumnPIIInfo columnInfo = resultFactory.createEmptyResult(tableName, columnName);

        // Attempt to recover unhealthy strategies
        healthMonitor.checkAndRecoverStrategies(nerStrategy::isServiceAvailable);

        // Create a pipeline context to group parameters
        PipelineContext context = new PipelineContext(
                connectionId, dbType, tableName, columnName,
                sampleData, columnInfo, startTime);

        // Process each stage in the pipeline
        processStages(context);

        // Determine if the column contains PII based on all detections
        columnInfo.setPiiDetected(!columnInfo.getDetections().isEmpty());

        log.debug("Detection pipeline completed for {}.{}, {} detections found",
                tableName, columnName, columnInfo.getDetections().size());

        long totalTime = System.currentTimeMillis() - startTime;
        metricsCollector.recordColumnProcessed(totalTime);
        metricsCollector.recordColumnProcessingTime(tableName, columnName, totalTime);

        // Cache the result
        cacheResult(connectionId, dbType, tableName, columnName, columnInfo);

        return columnInfo;
    }

    /**
     * Validates table and column names.
     */
    private void validateNames(String tableName, String columnName) {
        DatabaseUtils.validateTableName(tableName);
        DatabaseUtils.validateColumnName(columnName);
    }

    /**
     * Checks the cache for existing results.
     * 
     * @return Cached result or null if not found
     */
    private ColumnPIIInfo checkCache(String connectionId, String dbType, String tableName, String columnName) {
        if (cachingEnabled) {
            String cacheKey = cacheManager.generateCacheKey(connectionId, dbType, tableName, columnName);
            Map<String, ColumnPIIInfo> columnResultCache = cacheManager.getCache(COLUMN_RESULT_CACHE);

            if (columnResultCache != null && columnResultCache.containsKey(cacheKey)) {
                log.debug("Using cached result for {}.{}", tableName, columnName);
                return columnResultCache.get(cacheKey);
            }
        }
        return null;
    }

    /**
     * Process each stage in the configured pipeline.
     */
    private void processStages(PipelineContext ctx) {
        // Execute each stage in sequence according to configuration
        for (String stageName : pipelineConfig.getStages()) {
            // Skip stages that require sample data if none is available
            boolean shouldSkipDueToNoSamples = !ctx.hasSamples &&
                    (STRATEGY_REGEX.equals(stageName) || STRATEGY_NER.equals(stageName));

            PIIDetectionStrategy strategy = strategies.get(stageName);
            boolean isUnknownStage = strategy == null;

            if (shouldSkipDueToNoSamples) {
                log.debug("Skipping {} stage due to no samples for {}.{}",
                        stageName, ctx.tableName, ctx.columnName);
            } else if (isUnknownStage) {
                log.warn("Unknown pipeline stage: {}, skipping", stageName);
            } else if (executeStage(stageName, strategy, ctx) && earlyTerminationEnabled) {
                // Early termination if enabled and high-confidence match found
                log.debug("Pipeline stopping early after {} stage for {}.{}",
                        stageName, ctx.tableName, ctx.columnName);
                break;
            }
        }
    }

    /**
     * Executes a specific pipeline stage with the given strategy.
     * 
     * @return true if high-confidence detection found, false otherwise
     */
    private boolean executeStage(String stageName, PIIDetectionStrategy strategy, PipelineContext ctx) {

        if (shouldSkipStrategy(stageName, ctx.tableName, ctx.columnName, ctx.columnInfo)) {
            return false;
        }

        // Pre-filter samples for regex to improve performance
        List<Object> effectiveSamples = getEffectiveSamples(stageName, ctx.sampleData, ctx.tableName, ctx.columnName);
        if (effectiveSamples != null && effectiveSamples.isEmpty() && !STRATEGY_HEURISTIC.equals(stageName)) {
            return false;
        }

        long stageStart = System.currentTimeMillis();

        try {
            return processStrategyResult(
        strategy.detectColumnPII(
                ctx.connectionId, ctx.dbType, ctx.tableName, ctx.columnName,
                STRATEGY_HEURISTIC.equals(stageName) ? null : effectiveSamples),
        stageName, ctx);
        } catch (Exception e) {
            handleStrategyError(stageName, ctx.tableName, ctx.columnName, e, ctx.columnInfo);
        } finally {
            metricsCollector.recordDetectionTime(stageName, System.currentTimeMillis() - stageStart);
        }

        return false;
    }

    /**
     * Determines if a strategy should be skipped.
     */
    private boolean shouldSkipStrategy(String stageName, String tableName, String columnName,
            ColumnPIIInfo columnInfo) {
        if (!healthMonitor.isStrategyHealthy(stageName) ||
                (STRATEGY_NER.equals(stageName)
                        && (healthMonitor.isEmergencyModeActive() || !nerStrategy.isServiceAvailable()))) {

            // Extract the complex ternary operation into separate statements
            String reason;
            if (!healthMonitor.isStrategyHealthy(stageName)) {
                reason = "strategy unhealthy";
            } else if (!nerStrategy.isServiceAvailable()) {
                reason = "service unavailable";
            } else {
                reason = "emergency mode active";
            }

            log.debug("Skipping {} strategy for {}.{} ({})", stageName, tableName, columnName, reason);

            // Add metadata about skipped strategy
            Map<String, Object> metadata = columnInfo.getAdditionalInfo();
            metadata.put(stageName + "Skipped", true);
            metadata.put(stageName + "SkipReason", reason);

            return true;
        }
        return false;
    }

    /**
     * Gets the effective samples to use for a strategy.
     */
    private List<Object> getEffectiveSamples(String stageName, List<Object> samples, String tableName,
            String columnName) {
        if (STRATEGY_REGEX.equals(stageName) && samples != null) {
            List<Object> filtered = sampleFilterService.preFilterSamples(samples);
            if (filtered.isEmpty()) {
                log.debug("No valid samples after filtering for regex stage for {}.{}", tableName, columnName);
                return Collections.emptyList();
            }
            return filtered;
        }
        return samples;
    }

    /**
 * Processes the result from a strategy execution.
 * 
 * @return true if high-confidence detection found, false otherwise
 */
private boolean processStrategyResult(ColumnPIIInfo stageResult, String stageName, PipelineContext ctx) {
    // Strategy successful, mark as healthy
    healthMonitor.markStrategySuccess(stageName);

    if (stageResult.isPiiDetected()) {
        // Check if a detection has sufficient confidence for early termination
        Optional<PIITypeDetection> highConfidenceDetection = getHighConfidenceDetection(stageResult);

        if (highConfidenceDetection.isPresent()) {
            log.debug("{} detection sufficient for {}.{}, confidence={}",
                    stageName, ctx.tableName, ctx.columnName, 
                    highConfidenceDetection.get().getConfidence());

            ctx.columnInfo.setColumnType(stageResult.getColumnType());
            ctx.columnInfo.getDetections().add(highConfidenceDetection.get());
            ctx.columnInfo.setPiiDetected(true);

            // Record metrics
            recordStageMetrics(stageName, highConfidenceDetection.get(), 
                    ctx.tableName, ctx.columnName, ctx.startTime);

            // Cache result for high-confidence detections
            cacheResult(ctx.connectionId, ctx.dbType, ctx.tableName, ctx.columnName, ctx.columnInfo);

            return true;
        } else {
            // Add best detection and continue
            addBestDetection(stageName, stageResult, ctx.columnInfo);

            // Enhance with context if enabled and in heuristic stage
            if (contextEnhancementEnabled && STRATEGY_HEURISTIC.equals(stageName)) {
                contextEnhancer.enhanceConfidenceWithContext(ctx.tableName, ctx.columnName, ctx.columnInfo);
            }
        }
    } else {
        log.debug("No PII detected by {} strategy for {}.{}", 
                stageName, ctx.tableName, ctx.columnName);
    }

    return false;
}

    /**
     * Records metrics for a successful stage detection.
     */
    private void recordStageMetrics(String stageName, PIITypeDetection detection,
            String tableName, String columnName, long startTime) {
        if (STRATEGY_HEURISTIC.equals(stageName)) {
            metricsCollector.recordHeuristicDetection();
            metricsCollector.recordHeuristicPipelineStop();
        } else if (STRATEGY_REGEX.equals(stageName)) {
            metricsCollector.recordRegexDetection();
            metricsCollector.recordRegexPipelineStop();
        } else if (STRATEGY_NER.equals(stageName)) {
            metricsCollector.recordNerDetection();
            metricsCollector.recordNerPipelineStop();
        }

        metricsCollector.recordPiiTypeDetection(detection.getPiiType().name());

        long totalTime = System.currentTimeMillis() - startTime;
        metricsCollector.recordColumnProcessed(totalTime);
        metricsCollector.recordColumnProcessingTime(tableName, columnName, totalTime);
    }

    /**
     * Adds the highest confidence detection from a stage result to the column info.
     */
    private void addBestDetection(String stageName, ColumnPIIInfo stageResult, ColumnPIIInfo columnInfo) {
        Optional<PIITypeDetection> bestDetection = stageResult.getDetections().stream()
                .max(Comparator.comparingDouble(PIITypeDetection::getConfidence));

        if (bestDetection.isPresent()) {
            columnInfo.getDetections().add(bestDetection.get());

            if (STRATEGY_HEURISTIC.equals(stageName)) {
                metricsCollector.recordHeuristicDetection();
            } else if (STRATEGY_REGEX.equals(stageName)) {
                metricsCollector.recordRegexDetection();
            } else if (STRATEGY_NER.equals(stageName)) {
                metricsCollector.recordNerDetection();
            }

            metricsCollector.recordPiiTypeDetection(bestDetection.get().getPiiType().name());
        }
    }

    /**
     * Handles errors that occur during strategy execution.
     */
    private void handleStrategyError(String stageName, String tableName, String columnName,
            Exception e, ColumnPIIInfo columnInfo) {
        log.error("Error during {} detection on {}.{}: {}",
                stageName, tableName, columnName, e.getMessage(), e);

        // Mark strategy as potentially unhealthy
        healthMonitor.markStrategyFailure(stageName);

        // Add warning to result metadata
        Map<String, Object> metadata = columnInfo.getAdditionalInfo();
        metadata.put(stageName + "Failure", true);
        metadata.put(stageName + "Error", e.getMessage());

        log.warn("Continuing with other detection strategies after {} failure", stageName);
    }

    /**
     * Caches a result if caching is enabled.
     */
    private void cacheResult(String connectionId, String dbType, String tableName,
            String columnName, ColumnPIIInfo result) {
        if (cachingEnabled) {
            String cacheKey = cacheManager.generateCacheKey(connectionId, dbType, tableName, columnName);
            Map<String, ColumnPIIInfo> columnResultCache = cacheManager.getCache(COLUMN_RESULT_CACHE);
            if (columnResultCache != null) {
                columnResultCache.put(cacheKey, result);
            }
        }
    }

    /**
     * Finds a detection with high confidence, if it exists.
     *
     * @param columnInfo Column information
     * @return High confidence detection (optional)
     */
    private Optional<PIITypeDetection> getHighConfidenceDetection(ColumnPIIInfo columnInfo) {
        if (columnInfo == null || !columnInfo.isPiiDetected()) {
            return Optional.empty();
        }

        // Consider 90% or higher confidence as "high"
        double highConfidenceThreshold = 0.9;

        return columnInfo.getDetections().stream()
                .filter(detection -> detection.getConfidence() >= highConfidenceThreshold)
                .max(Comparator.comparingDouble(PIITypeDetection::getConfidence));
    }

    /**
     * Updates the confidence threshold for all strategies.
     *
     * @param threshold New confidence threshold
     */
    public void setConfidenceThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw PIIDetectionException.configError("Confidence threshold must be between 0.0 and 1.0");
        }

        // Propagate the change to all strategies
        strategies.values().forEach(strategy -> strategy.setConfidenceThreshold(threshold));

        // Clear the column result cache since threshold changed
        clearCache();
    }

    /**
     * Forces a health check on all strategies and attempts recovery.
     */
    public Map<String, Boolean> forceHealthCheck() {
        return healthMonitor.forceHealthCheck(nerStrategy::isServiceAvailable);
    }

    /**
     * Resets the emergency mode flag.
     */
    public void resetEmergencyMode() {
        healthMonitor.resetEmergencyMode();
    }

    /**
     * Gets the current health status of all strategies.
     */
    public Map<String, Object> getHealthStatus() {
        return healthMonitor.getHealthStatus();
    }

    /**
     * Clears the column result cache.
     */
    public void clearCache() {
        if (cachingEnabled) {
            Map<String, ColumnPIIInfo> columnResultCache = cacheManager.getCache(COLUMN_RESULT_CACHE);
            if (columnResultCache != null) {
                log.info("Clearing column result cache with {} entries", columnResultCache.size());
                columnResultCache.clear();
            } else {
                log.warn("Attempted to clear column result cache, but it was not found");
            }
        }
    }
}