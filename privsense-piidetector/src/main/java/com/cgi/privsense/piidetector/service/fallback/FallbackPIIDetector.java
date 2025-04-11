package com.cgi.privsense.piidetector.service.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback PII detector using regex patterns.
 * Used when external NER services are unavailable.
 */
@Component
public class FallbackPIIDetector {
    private static final Logger log = LoggerFactory.getLogger(FallbackPIIDetector.class);

    // Minimal fallback patterns for critical PII types when NER service is down
    private static final Map<String, Pattern> FALLBACK_PATTERNS = new HashMap<>();
    static {
        // Email pattern
        FALLBACK_PATTERNS.put("EMAIL", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}"));
        // Phone pattern (simple international format)
        FALLBACK_PATTERNS.put("PHONE", Pattern.compile("\\+?\\d{10,15}"));
        // SSN pattern (US format)
        FALLBACK_PATTERNS.put("SSN", Pattern.compile("\\d{3}-\\d{2}-\\d{4}"));
        // Credit card pattern (simplified)
        FALLBACK_PATTERNS.put("CREDIT_CARD", Pattern.compile("\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}"));
    }

    /**
     * Applies fallback detection to a set of column samples.
     *
     * @param columnDataMap Map of column names to text samples
     * @return Map of column names to detected entity types with confidence levels
     */
    public Map<String, Map<String, Double>> batchDetectPII(Map<String, List<String>> columnDataMap) {
        log.debug("Applying fallback detection to {} columns", columnDataMap.size());
        Map<String, Map<String, Double>> results = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : columnDataMap.entrySet()) {
            String columnName = entry.getKey();
            List<String> samples = entry.getValue();

            if (samples == null || samples.isEmpty()) {
                results.put(columnName, Collections.emptyMap());
                continue;
            }

            Map<String, Double> columnResults = detectPII(samples);
            results.put(columnName, columnResults);
        }

        return results;
    }

    /**
     * Applies fallback detection to a list of text samples.
     *
     * @param samples List of text samples to analyze
     * @return Map of detected entity types with confidence levels
     */
    public Map<String, Double> detectPII(List<String> samples) {
        log.debug("Applying fallback detection to {} samples", samples.size());
        
        if (samples == null || samples.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> matchCounts = countPatternMatches(samples);
        return calculateConfidenceScores(matchCounts, samples.size());
    }

    /**
     * Counts pattern matches across all samples.
     * 
     * @param samples List of text samples to analyze
     * @return Map of entity types to match counts
     */
    private Map<String, Integer> countPatternMatches(List<String> samples) {
        Map<String, Integer> matchCounts = new HashMap<>();

        for (String sample : samples) {
            if (sample == null || sample.isEmpty()) {
                continue;
            }

            applyPatternsToSample(sample, matchCounts);
        }

        return matchCounts;
    }

    /**
     * Applies all patterns to a single sample and updates match counts.
     * 
     * @param sample      The text sample to check
     * @param matchCounts Map to update with match counts
     */
    private void applyPatternsToSample(String sample, Map<String, Integer> matchCounts) {
        for (Map.Entry<String, Pattern> patternEntry : FALLBACK_PATTERNS.entrySet()) {
            String piiType = patternEntry.getKey();
            Pattern pattern = patternEntry.getValue();

            Matcher matcher = pattern.matcher(sample);
            if (matcher.find()) {
                matchCounts.put(piiType, matchCounts.getOrDefault(piiType, 0) + 1);
            }
        }
    }

    /**
     * Calculates confidence scores based on match counts.
     * 
     * @param matchCounts Map of entity types to match counts
     * @param sampleSize  Total number of samples
     * @return Map of entity types to confidence levels
     */
    private Map<String, Double> calculateConfidenceScores(Map<String, Integer> matchCounts, int sampleSize) {
        Map<String, Double> results = new HashMap<>();

        for (Map.Entry<String, Integer> countEntry : matchCounts.entrySet()) {
            String piiType = countEntry.getKey();
            int count = countEntry.getValue();
            double confidence = (double) count / sampleSize * 0.85; // Cap at 85% confidence for regex fallback

            if (confidence > 0.25) { // Only include types with reasonable confidence
                results.put(piiType, confidence);
            }
        }

        return results;
    }
}