package com.cgi.privsense.piidetector.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for NER (Named Entity Recognition) service results.
 * Improves performance by reusing previous detection results for identical samples.
 */
@Component
public class NERResultsCache {
    private static final Logger log = LoggerFactory.getLogger(NERResultsCache.class);
    
    // Cache for NER results to avoid redundant calls
    private final Map<String, Map<String, Double>> nerResultsCache = new ConcurrentHashMap<>();
    
    /**
     * Retrieves cached results for a column's samples if available.
     *
     * @param columnName Column name
     * @param samples Text samples for this column
     * @return Cached entity detection results, or null if not cached
     */
    public Map<String, Double> getColumnResults(String columnName, List<String> samples) {
        String cacheKey = generateCacheKey(columnName, samples);
        return nerResultsCache.get(cacheKey);
    }
    
    /**
     * Retrieves cached results for text samples if available.
     *
     * @param samples Text samples to analyze
     * @return Cached entity detection results, or null if not cached
     */
    public Map<String, Double> getResults(List<String> samples) {
        String cacheKey = generateCacheKey(null, samples);
        return nerResultsCache.get(cacheKey);
    }
    
    /**
     * Stores NER results in the cache.
     *
     * @param columnName Column name (can be null for non-column-specific results)
     * @param samples Text samples
     * @param results Detection results
     */
    public void cacheResults(String columnName, List<String> samples, Map<String, Double> results) {
        String cacheKey = generateCacheKey(columnName, samples);
        nerResultsCache.put(cacheKey, results);
        log.debug("Cached NER results for {} samples", samples.size());
    }
    
    /**
     * Clears the NER results cache.
     */
    public void clearCache() {
        int size = nerResultsCache.size();
        nerResultsCache.clear();
        log.info("NER results cache cleared ({} entries)", size);
    }
    
    /**
     * Returns the size of the cache.
     * 
     * @return Number of entries in the cache
     */
    public int size() {
        return nerResultsCache.size();
    }
    
    /**
     * Generates a consistent cache key from column name and samples.
     *
     * @param columnName Column name (can be null)
     * @param samples Text samples
     * @return Cache key string
     */
    private String generateCacheKey(String columnName, List<String> samples) {
        String prefix = (columnName != null) ? columnName + "_" : "";
        return prefix + String.join("_", samples);
    }
}