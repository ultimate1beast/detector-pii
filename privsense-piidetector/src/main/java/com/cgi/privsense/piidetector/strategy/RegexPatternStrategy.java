/*
 * RegexPatternStrategy.java - Optimized strategy using regular expressions to detect PII
 */
package com.cgi.privsense.piidetector.strategy;

import com.cgi.privsense.piidetector.model.ColumnPIIInfo;
import com.cgi.privsense.piidetector.model.enums.DetectionMethod;
import com.cgi.privsense.piidetector.model.enums.PIIType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;



/**
 * Strategy based on regular expressions for PII detection.
 * Uses regex patterns to analyze the content of data samples.
 */
@Component
public class RegexPatternStrategy extends AbstractPIIDetectionStrategy {
    private static final Map<PIIType, Pattern> REGEX_PATTERNS = new EnumMap<>(PIIType.class);

    // Pre-filters for quick rejection before applying expensive regex
    private static final Map<PIIType, QuickCheckFunction> QUICK_CHECKS = new EnumMap<>(PIIType.class);

    // Result cache to avoid redundant processing
    private final Map<String, ColumnPIIInfo> resultCache = new ConcurrentHashMap<>();

    static {
        // Email - simplified
        REGEX_PATTERNS.put(PIIType.EMAIL,
                Pattern.compile("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"));

        // Phone number - simplified
        REGEX_PATTERNS.put(PIIType.PHONE_NUMBER,
                Pattern.compile("^(\\+\\d{1,3}[- ]?)?(\\d{3}[- ]?){1,2}\\d{4}$"));

        // Credit card number - simplified
        REGEX_PATTERNS.put(PIIType.CREDIT_CARD,
                Pattern.compile("^\\d{13,19}$")); // Simple length check, implement Luhn algorithm separately

        // IP address - simplified
        REGEX_PATTERNS.put(PIIType.IP_ADDRESS,
                Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$"));

        // Postal code - simplified
        REGEX_PATTERNS.put(PIIType.POSTAL_CODE,
                Pattern.compile("^\\d{5}(-\\d{4})?$"));

        // Social security number
        REGEX_PATTERNS.put(PIIType.NATIONAL_ID,
                Pattern.compile("^\\d{3}-\\d{2}-\\d{4}$"));

        // Date in ISO format
        REGEX_PATTERNS.put(PIIType.DATE_OF_BIRTH,
                Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$"));

        // Date and time in ISO format
        REGEX_PATTERNS.put(PIIType.DATE_TIME,
                Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"));

        // Setup quick checks
        QUICK_CHECKS.put(PIIType.CREDIT_CARD, s -> 
            s.length() >= 13 && s.length() <= 19 && s.matches("\\d+")
        );

        QUICK_CHECKS.put(PIIType.EMAIL, s -> 
            s.contains("@")
        );

        QUICK_CHECKS.put(PIIType.PHONE_NUMBER, s -> 
            s.matches(".*\\d.*") && s.length() >= 7
        );

        QUICK_CHECKS.put(PIIType.IP_ADDRESS, s -> 
            s.matches(".*\\d.*") && s.contains(".")
        );
    }

    /**
     * Helper class to encapsulate detection parameters.
     */
    private static class DetectionParams {
        private final PIIType piiType;
        private final double matchRatio;
        private final long matchTime;
        private final long matchCount;
        private final List<String> matchingData;
        private final Pattern pattern;
        private final int sampleSize;

        public DetectionParams(PIIType piiType, double matchRatio, long matchTime, 
                              long matchCount, List<String> matchingData, 
                              Pattern pattern, int sampleSize) {
            this.piiType = piiType;
            this.matchRatio = matchRatio;
            this.matchTime = matchTime;
            this.matchCount = matchCount;
            this.matchingData = matchingData;
            this.pattern = pattern;
            this.sampleSize = sampleSize;
        }
    }

    @Override
    public String getName() {
        return "RegexPatternStrategy";
    }

    @Override
    public ColumnPIIInfo detectColumnPII(String connectionId, String dbType, String tableName,
                                         String columnName, List<Object> sampleData) {
        // Generate cache key from inputs
        String cacheKey = generateCacheKey(dbType, tableName, columnName, sampleData);

        // Check cache first
        if (resultCache.containsKey(cacheKey)) {
            return resultCache.get(cacheKey);
        }

        // Build initial result
        ColumnPIIInfo result = initializeResult(columnName, tableName);

        // Skip processing for empty data
        if (sampleData == null || sampleData.isEmpty()) {
            return result;
        }

        // Pre-check data type suitability
        boolean isAllNumeric = isAllNumeric(sampleData);
        boolean isAllString = isAllString(sampleData);

        // Process each PII type
        processPIITypes(sampleData, result, isAllNumeric, isAllString);

        // Store in cache
        resultCache.put(cacheKey, result);

        return result;
    }

    /**
     * Generates a cache key from the input parameters.
     */
    private String generateCacheKey(String dbType, String tableName, String columnName, List<Object> sampleData) {
        return dbType + ":" + tableName + ":" + columnName + ":" +
                (sampleData != null ? sampleData.hashCode() : "null");
    }

    /**
     * Initializes the result object.
     */
    private ColumnPIIInfo initializeResult(String columnName, String tableName) {
        return ColumnPIIInfo.builder()
                .columnName(columnName)
                .tableName(tableName)
                .piiDetected(false)
                .detections(new ArrayList<>())
                .build();
    }

    /**
     * Processes each PII type against the sample data.
     */
    private void processPIITypes(List<Object> sampleData, ColumnPIIInfo result, boolean isAllNumeric, boolean isAllString) {
        for (Map.Entry<PIIType, Pattern> entry : REGEX_PATTERNS.entrySet()) {
            PIIType piiType = entry.getKey();
            
            // Skip inappropriate type checks
            if (shouldSkipPIIType(piiType, isAllNumeric, isAllString)) {
                continue;
            }
            
            processPIITypeMatches(piiType, entry.getValue(), sampleData, result);
        }
    }

    /**
     * Determines if a PII type check should be skipped based on data characteristics.
     */
    private boolean shouldSkipPIIType(PIIType piiType, boolean isAllNumeric, boolean isAllString) {
        return ((piiType == PIIType.CREDIT_CARD || piiType == PIIType.NATIONAL_ID) && !isAllNumeric) ||
               ((piiType == PIIType.EMAIL || piiType == PIIType.IP_ADDRESS) && !isAllString);
    }

    /**
     * Processes matches for a single PII type.
     */
    private void processPIITypeMatches(PIIType piiType, Pattern pattern, List<Object> sampleData, ColumnPIIInfo result) {
        long startTime = System.currentTimeMillis();
        
        // Get matching data for this pattern
        List<String> matchingData = findMatchingData(piiType, pattern, sampleData);
        
        long matchTime = System.currentTimeMillis() - startTime;
        long matchCount = matchingData.size();
        double matchRatio = (double) matchCount / sampleData.size();

        // If enough data matches, consider as PII
        if (matchRatio >= confidenceThreshold) {
            DetectionParams params = new DetectionParams(
                piiType, matchRatio, matchTime, matchCount, 
                matchingData, pattern, sampleData.size()
            );
            addPIIDetection(params, result);
        }
    }

    /**
     * Finds data samples that match a given pattern for a PII type.
     */
    private List<String> findMatchingData(PIIType piiType, Pattern pattern, List<Object> sampleData) {
        List<String> matchingData = new ArrayList<>();
        QuickCheckFunction quickCheck = QUICK_CHECKS.get(piiType);
        
        for (Object obj : sampleData) {
            // Use a single condition with logical AND to replace multiple continues
            if (obj != null) {
                String s = obj.toString().trim();
                
                boolean passesAllChecks = !s.isEmpty() && 
                                         (quickCheck == null || quickCheck.check(s));
                
                if (passesAllChecks && pattern.matcher(s).matches()) {
                    matchingData.add(s);
                }
            }
        }
        
        return matchingData;
    }

    /**
     * Adds a PII detection to the result.
     */
    private void addPIIDetection(DetectionParams params, ColumnPIIInfo result) {
        var detection = createDetection(
                params.piiType,
                params.matchRatio,
                DetectionMethod.REGEX_PATTERN.name());

        // Add detailed metadata
        Map<String, Object> metadata = detection.getDetectionMetadata();
        metadata.put("matchRatio", params.matchRatio);
        metadata.put("sampleSize", params.sampleSize);
        metadata.put("matchCount", params.matchCount);
        metadata.put("matchTimeMs", params.matchTime);
        metadata.put("patternUsed", params.pattern.pattern());

        // Add some matching examples (limit to 3)
        List<String> matchingExamples = params.matchingData.stream()
                .limit(3)
                .toList();
        metadata.put("matchingExamples", matchingExamples);

        // Add the detection to the result
        result.addDetection(detection);
    }

    @Override
    public boolean isApplicable(boolean hasMetadata, boolean hasSampleData) {
        // This strategy requires data samples
        return hasSampleData;
    }

    /**
     * Checks if all samples are numeric.
     *
     * @param samples List of data samples
     * @return true if all non-null samples are numeric
     */
    private boolean isAllNumeric(List<Object> samples) {
        if (samples == null || samples.isEmpty()) {
            return false;
        }

        for (Object obj : samples) {
            if (obj != null) {
                String s = obj.toString().trim();
                if (!s.isEmpty() && !s.matches("-?\\d+(\\.\\d+)?")) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if all samples are strings or can be meaningfully converted to strings.
     *
     * @param samples List of data samples
     * @return true if all non-null samples are strings or convertible types
     */
    private boolean isAllString(List<Object> samples) {
        if (samples == null || samples.isEmpty()) {
            return false;
        }

        for (Object obj : samples) {
            if (obj != null && !(obj instanceof String) && 
                !(obj instanceof Number || obj instanceof Date || obj instanceof Boolean)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Clears the result cache.
     */
    public void clearCache() {
        resultCache.clear();
    }

    /**
     * Functional interface for quick pattern pre-checks.
     */
    @FunctionalInterface
    private interface QuickCheckFunction {
        boolean check(String input);
    }
}