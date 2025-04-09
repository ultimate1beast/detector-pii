package com.cgi.privsense.piidetector.service;

import com.cgi.privsense.piidetector.model.ColumnPIIInfo;
import com.cgi.privsense.piidetector.model.PIITypeDetection;
import com.cgi.privsense.piidetector.model.enums.PIIType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory class for creating standardized PII detection results.
 * Reduces duplication of result creation logic across the codebase.
 */
@Component
public class DetectionResultFactory {
    
    /**
     * Creates an empty result for a column.
     *
     * @param tableName Table name
     * @param columnName Column name
     * @return Empty column PII info
     */
    public ColumnPIIInfo createEmptyResult(String tableName, String columnName) {
        return ColumnPIIInfo.builder()
                .columnName(columnName)
                .tableName(tableName)
                .piiDetected(false)
                .detections(new ArrayList<>())
                .additionalInfo(new HashMap<>())
                .build();
    }
    
    /**
     * Creates a PII detection with initialized detection metadata.
     *
     * @param type PII type
     * @param confidence Confidence level
     * @param method Detection method
     * @param confidenceThreshold The current confidence threshold
     * @return PII detection object
     */
    public PIITypeDetection createDetection(PIIType type, double confidence, String method, double confidenceThreshold) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Add basic metadata common to all strategies
        metadata.put("confidenceThreshold", confidenceThreshold);
        metadata.put("timestamp", System.currentTimeMillis());
        
        return PIITypeDetection.builder()
                .piiType(type)
                .confidence(confidence)
                .detectionMethod(method)
                .detectionMetadata(metadata)
                .build();
    }
    
    /**
     * Creates a PII detection with custom metadata.
     *
     * @param type PII type
     * @param confidence Confidence level
     * @param method Detection method
     * @param customMetadata Additional metadata to include
     * @param confidenceThreshold The current confidence threshold
     * @return PII detection object
     */
    public PIITypeDetection createDetection(PIIType type, double confidence, String method, 
                                         Map<String, Object> customMetadata, double confidenceThreshold) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Add custom metadata if provided
        if (customMetadata != null) {
            metadata.putAll(customMetadata);
        }
        
        // Add basic metadata
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