package com.cgi.privsense.piidetector.service;

import com.cgi.privsense.piidetector.model.ColumnPIIInfo;
import com.cgi.privsense.piidetector.model.PIITypeDetection;
import com.cgi.privsense.piidetector.model.enums.PIIType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Service that enhances detection confidence based on contextual information.
 * Analyzes column names and patterns to improve PII detection confidence.
 */
@Component
public class PIIContextEnhancer {
    private static final Logger log = LoggerFactory.getLogger(PIIContextEnhancer.class);
    
    // Enhancement configuration constants
    private static final double NAME_ENHANCEMENT_FACTOR = 1.2;
    private static final double STANDARD_ENHANCEMENT_FACTOR = 1.15;
    private static final double MAX_CONFIDENCE = 0.95;
    
    // Map of PII types to relevant keywords that indicate that type
    private static final Map<PIIType, List<String>> TYPE_KEYWORDS = new EnumMap<>(PIIType.class);
    
    static {
        // Initialize keyword mappings for each PII type
        TYPE_KEYWORDS.put(PIIType.FIRST_NAME, Arrays.asList("name", "nom", "prenom", "firstname", "first"));
        TYPE_KEYWORDS.put(PIIType.LAST_NAME, Arrays.asList("name", "nom", "surname", "lastname", "last"));
        TYPE_KEYWORDS.put(PIIType.FULL_NAME, Arrays.asList("name", "nom", "surname", "fullname", "full"));
        TYPE_KEYWORDS.put(PIIType.EMAIL, Arrays.asList("email", "mail", "contact", "e-mail"));
        TYPE_KEYWORDS.put(PIIType.PHONE_NUMBER, Arrays.asList("phone", "tel", "mobile", "contact", "cell"));
        TYPE_KEYWORDS.put(PIIType.ADDRESS, Arrays.asList("address", "addr", "street"));
        TYPE_KEYWORDS.put(PIIType.ZIPCODE, Arrays.asList("zip", "postal", "code"));
        TYPE_KEYWORDS.put(PIIType.CITY, Arrays.asList("city", "town", "address", "ville"));
        TYPE_KEYWORDS.put(PIIType.ID_NUMBER, Arrays.asList("id", "identifier", "identity", "national"));
        TYPE_KEYWORDS.put(PIIType.SSN, Arrays.asList("ssn", "social", "national", "identity", "passport"));
    }

    /**
     * Enhances detection confidence based on context (adjacent columns and patterns).
     * Applies more comprehensive context rules beyond just name fields.
     *
     * @param tableName Table name
     * @param columnName Column name
     * @param columnInfo Column information to enhance
     * @return Enhanced column information
     */
    public ColumnPIIInfo enhanceConfidenceWithContext(String tableName, String columnName, ColumnPIIInfo columnInfo) {
        if (columnInfo == null || !columnInfo.isPiiDetected() || columnInfo.getDetections().isEmpty()) {
            return columnInfo;
        }

        // Normalize the column name for pattern matching
        String normalizedName = columnName.toLowerCase();
        
        // Track if any enhancements were applied to log meaningful information
        boolean wasEnhanced = false;
        
        // Apply context rules for each detection
        for (PIITypeDetection detection : columnInfo.getDetections()) {
            PIIType type = detection.getPiiType();
            double originalConfidence = detection.getConfidence();
            
            // Store original confidence in metadata if we haven't already
            if (!detection.getDetectionMetadata().containsKey("originalConfidence")) {
                detection.getDetectionMetadata().put("originalConfidence", originalConfidence);
            }
            
            // Special case for ID columns to avoid enhancing pure ID columns
            if ((type == PIIType.ID_NUMBER || type == PIIType.SSN) && normalizedName.equals("id")) {
                continue;
            }
            
            // Apply the appropriate enhancement based on PII type
            if (enhanceDetection(type, normalizedName, detection)) {
                wasEnhanced = true;
            }
        }
        
        if (wasEnhanced) {
            log.debug("Applied context enhancement to column {}.{}", tableName, columnName);
        }
        
        return columnInfo;
    }
    
    /**
     * Enhances a single detection based on column name and PII type.
     * 
     * @param type PII type
     * @param normalizedName Normalized column name
     * @param detection The detection to enhance
     * @return true if enhancement was applied
     */
    private boolean enhanceDetection(PIIType type, String normalizedName, PIITypeDetection detection) {
        List<String> relevantKeywords = TYPE_KEYWORDS.get(type);
        
        if (relevantKeywords == null) {
            return false; // No keywords defined for this type
        }
        
        // Check if any relevant keyword is contained in the column name
        for (String keyword : relevantKeywords) {
            if (normalizedName.contains(keyword)) {
                // Determine enhancement factor based on PII type
                double enhancementFactor = isNameType(type) ? NAME_ENHANCEMENT_FACTOR : STANDARD_ENHANCEMENT_FACTOR;
                
                // Apply enhancement
                double originalConfidence = detection.getConfidence();
                double enhancedConfidence = Math.min(originalConfidence * enhancementFactor, MAX_CONFIDENCE);
                
                // Only update if enhancement actually increases confidence
                if (enhancedConfidence > originalConfidence) {
                    detection.setConfidence(enhancedConfidence);
                    detection.getDetectionMetadata().put("contextEnhanced", true);
                    detection.getDetectionMetadata().put("enhancementFactor", enhancementFactor);
                    return true;
                }
                
                break; // No need to check other keywords once we've found a match
            }
        }
        
        return false;
    }
    
    /**
     * Determines if a PII type is a name-related type.
     * 
     * @param type PII type to check
     * @return true if it's a name-related type
     */
    private boolean isNameType(PIIType type) {
        return type == PIIType.FIRST_NAME || type == PIIType.LAST_NAME || type == PIIType.FULL_NAME;
    }
}