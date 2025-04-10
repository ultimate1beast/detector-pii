/*
 * CompositePIIDetectionStrategy.java - Optimized composite strategy for PII detection
 */
package com.cgi.privsense.piidetector.strategy;

import com.cgi.privsense.piidetector.api.PIIDetectionStrategy;
import com.cgi.privsense.piidetector.model.ColumnPIIInfo;
import com.cgi.privsense.piidetector.model.enums.DetectionMethod;
import com.cgi.privsense.piidetector.model.enums.PIIType;
import com.cgi.privsense.piidetector.model.PIITypeDetection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Composite strategy that combines results from multiple strategies.
 * Implements the Composite pattern.
 */
@Component
public class CompositePIIDetectionStrategy extends AbstractPIIDetectionStrategy {
    private final List<PIIDetectionStrategy> strategies = new ArrayList<>();

    // Cache for detection results
    private final Map<String, ColumnPIIInfo> resultCache = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "CompositeStrategy";
    }

    /**
     * Adds a strategy to the composition.
     *
     * @param strategy Strategy to add
     */
    public void addStrategy(PIIDetectionStrategy strategy) {
        strategies.add(strategy);
    }

    @Override
    public ColumnPIIInfo detectColumnPII(String connectionId, String dbType, String tableName,
            String columnName, List<Object> sampleData) {
        // Generate cache key
        String cacheKey = generateCacheKey(dbType, tableName, columnName, sampleData);

        // Check cache first
        if (resultCache.containsKey(cacheKey)) {
            return resultCache.get(cacheKey);
        }

        ColumnPIIInfo result = ColumnPIIInfo.builder()
                .columnName(columnName)
                .tableName(tableName)
                .piiDetected(false)
                .detections(new ArrayList<>())
                .build();

        // Apply each strategy and collect results
        Map<PIIType, List<PIITypeDetection>> detectionsByType = collectStrategyResults(
                connectionId, dbType, tableName, columnName, sampleData);

        // Process aggregated results
        processAggregatedResults(detectionsByType, result);

        // Store in cache
        resultCache.put(cacheKey, result);

        return result;
    }

    /**
     * Generates a cache key based on strategies and input parameters.
     */
    private String generateCacheKey(String dbType, String tableName, String columnName, List<Object> sampleData) {
        String strategiesKey = strategies.stream()
                .map(PIIDetectionStrategy::getName)
                .sorted()
                .reduce("", (a, b) -> a + ":" + b);

        return strategiesKey + ":" + dbType + ":" + tableName + ":" + columnName + ":" +
                (sampleData != null ? sampleData.hashCode() : "null");
    }

    /**
     * Collects and aggregates results from all applicable strategies.
     */
    private Map<PIIType, List<PIITypeDetection>> collectStrategyResults(String connectionId, String dbType,
            String tableName, String columnName,
            List<Object> sampleData) {
        boolean hasSampleData = sampleData != null && !sampleData.isEmpty();
        boolean hasMetadata = true;

        Map<PIIType, List<PIITypeDetection>> detectionsByType = new EnumMap<>(PIIType.class);

        for (PIIDetectionStrategy strategy : strategies) {
            if (strategy.isApplicable(hasMetadata, hasSampleData)) {
                ColumnPIIInfo strategyResult = strategy.detectColumnPII(
                        connectionId, dbType, tableName, columnName, sampleData);

                if (strategyResult.isPiiDetected()) {
                    aggregateDetections(strategyResult.getDetections(), detectionsByType);
                }
            }
        }

        return detectionsByType;
    }

    /**
     * Aggregates detections by PII type.
     */
    private void aggregateDetections(List<PIITypeDetection> detections,
            Map<PIIType, List<PIITypeDetection>> detectionsByType) {
        for (PIITypeDetection detection : detections) {
            PIIType piiType = detection.getPiiType();
            detectionsByType.computeIfAbsent(piiType, k -> new ArrayList<>()).add(detection);
        }
    }

    /**
     * Processes aggregated results and adds detections to the result when
     * confidence is sufficient.
     */
    private void processAggregatedResults(Map<PIIType, List<PIITypeDetection>> detectionsByType,
            ColumnPIIInfo result) {
        for (Map.Entry<PIIType, List<PIITypeDetection>> entry : detectionsByType.entrySet()) {
            PIIType piiType = entry.getKey();
            List<PIITypeDetection> detections = entry.getValue();

            double avgConfidence = calculateAverageConfidence(detections);

            if (avgConfidence >= confidenceThreshold) {
                result.addDetection(createDetection(
                        piiType,
                        avgConfidence,
                        DetectionMethod.COMPOSITE.name()));
            }
        }
    }

    /**
     * Calculates average confidence from a list of detections.
     */
    private double calculateAverageConfidence(List<PIITypeDetection> detections) {
        return detections.stream()
                .mapToDouble(PIITypeDetection::getConfidence)
                .average()
                .orElse(0.0);
    }

    @Override
    public boolean isApplicable(boolean hasMetadata, boolean hasSampleData) {
        // The composite strategy is applicable if at least one of its strategies is
        return strategies.stream()
                .anyMatch(strategy -> strategy.isApplicable(hasMetadata, hasSampleData));
    }

    @Override
    public void setConfidenceThreshold(double threshold) {
        super.setConfidenceThreshold(threshold);
        // Propagate threshold to all strategies
        strategies.forEach(strategy -> strategy.setConfidenceThreshold(threshold));
        // Clear cache when threshold changes
        resultCache.clear();
    }

    /**
     * Clears the result cache.
     */
    public void clearCache() {
        resultCache.clear();
    }
}