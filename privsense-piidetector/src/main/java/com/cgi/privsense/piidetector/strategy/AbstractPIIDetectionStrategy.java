/*
 * AbstractPIIDetectionStrategy.java - Abstract strategy for PII detection
 */
package com.cgi.privsense.piidetector.strategy;

import com.cgi.privsense.piidetector.api.PIIDetectionStrategy;
import com.cgi.privsense.piidetector.model.*;
import com.cgi.privsense.piidetector.model.enums.PIIType;
import com.cgi.privsense.piidetector.service.DetectionResultFactory;
import com.cgi.privsense.piidetector.service.PIIDetectionCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Base strategy for PII detection.
 * Provides common functionality for all strategies.
 */
public abstract class AbstractPIIDetectionStrategy implements PIIDetectionStrategy {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected double confidenceThreshold = 0.7;
    
    // These can be null if not explicitly injected
    protected PIIDetectionCacheManager cacheManager;
    protected DetectionResultFactory resultFactory;
    
    /**
     * Sets the cache manager for this strategy.
     * Should be called by Spring injection in concrete implementations.
     *
     * @param cacheManager Cache manager instance
     */
    public void setCacheManager(PIIDetectionCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    /**
     * Sets the result factory for this strategy.
     * Should be called by Spring injection in concrete implementations.
     *
     * @param resultFactory Result factory instance
     */
    public void setResultFactory(DetectionResultFactory resultFactory) {
        this.resultFactory = resultFactory;
    }

    @Override
    public void setConfidenceThreshold(double threshold) {
        this.confidenceThreshold = threshold;
    }
    
    /**
     * Generates a standardized cache key for detection results.
     *
     * @param connectionId Connection ID
     * @param dbType Database type
     * @param tableName Table name
     * @param columnName Column name
     * @param data Sample data (for hash generation)
     * @return Cache key
     */
    protected String generateCacheKey(String connectionId, String dbType, String tableName, 
                                   String columnName, List<?> data) {
        if (cacheManager != null) {
            return cacheManager.generateCacheKey(
                getName(),
                connectionId,
                dbType,
                tableName,
                columnName,
                data != null ? String.valueOf(data.hashCode()) : "null"
            );
        } else {
            // Fallback if cache manager is not available
            return String.join(":", 
                getName(),
                connectionId,
                dbType != null ? dbType : "null",
                tableName,
                columnName,
                data != null ? String.valueOf(data.hashCode()) : "null"
            );
        }
    }

    /**
     * Creates a PII detection with initialized detection metadata.
     *
     * @param type PII type
     * @param confidence Confidence level
     * @param method Detection method
     * @return PII detection object
     */
    protected PIITypeDetection createDetection(PIIType type, double confidence, String method) {
        if (resultFactory != null) {
            return resultFactory.createDetection(type, confidence, method, confidenceThreshold);
        } else {
            // Fallback to original implementation
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("confidenceThreshold", confidenceThreshold);
            metadata.put("timestamp", System.currentTimeMillis());

            return PIITypeDetection.builder()
                    .piiType(type)
                    .confidence(confidence)
                    .detectionMethod(method)
                    .detectionMetadata(metadata)
                    .build();
        }
    }

    /**
     * Creates a PII detection with custom metadata.
     *
     * @param type PII type
     * @param confidence Confidence level
     * @param method Detection method
     * @param metadata Additional metadata to include
     * @return PII detection object
     */
    protected PIITypeDetection createDetection(PIIType type, double confidence, String method, Map<String, Object> metadata) {
        if (resultFactory != null) {
            return resultFactory.createDetection(type, confidence, method, metadata, confidenceThreshold);
        } else {
            // Fallback to original implementation
            Map<String, Object> safeMetadata = metadata != null ? metadata : new HashMap<>();
            safeMetadata.put("confidenceThreshold", confidenceThreshold);
            safeMetadata.put("timestamp", System.currentTimeMillis());

            return PIITypeDetection.builder()
                    .piiType(type)
                    .confidence(confidence)
                    .detectionMethod(method)
                    .detectionMetadata(safeMetadata)
                    .build();
        }
    }
    
    /**
     * Creates an empty result for a column.
     *
     * @param tableName Table name
     * @param columnName Column name
     * @return Empty column PII info
     */
    protected ColumnPIIInfo createEmptyColumnResult(String tableName, String columnName) {
        if (resultFactory != null) {
            return resultFactory.createEmptyResult(tableName, columnName);
        } else {
            // Fallback to direct creation
            return ColumnPIIInfo.builder()
                    .tableName(tableName)
                    .columnName(columnName)
                    .piiDetected(false)
                    .detections(new ArrayList<>())
                    .additionalInfo(new HashMap<>())
                    .build();
        }
    }
}