package com.cgi.privsense.piidetector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized cache manager for all PII detection related caches.
 * Provides a unified way to register, access, and clear caches.
 */
@Component
public class PIIDetectionCacheManager {
    private static final Logger log = LoggerFactory.getLogger(PIIDetectionCacheManager.class);
    
    private final Map<String, Object> caches = new HashMap<>();
    
    /**
     * Registers a cache with the manager.
     *
     * @param cacheName Name of the cache
     * @param cache The cache object (typically a Map)
     */
    public void registerCache(String cacheName, Map<?, ?> cache) {
        caches.put(cacheName, cache);
        log.debug("Registered cache: {} (size: {})", cacheName, cache.size());
    }
    
    /**
     * Clears all registered caches.
     */
    public void clearAll() {
        log.info("Clearing all caches ({} total)", caches.size());
        for (Map.Entry<String, Object> entry : caches.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<?, ?> cache = (Map<?, ?>) entry.getValue();
                int size = cache.size();
                cache.clear();
                log.debug("Cleared cache '{}' with {} entries", entry.getKey(), size);
            }
        }
    }
    
    /**
     * Clears a specific cache by name.
     *
     * @param cacheName Name of the cache to clear
     * @return true if the cache was found and cleared, false otherwise
     */
    public boolean clearCache(String cacheName) {
        Object cache = caches.get(cacheName);
        if (cache instanceof Map) {
            int size = ((Map<?, ?>) cache).size();
            ((Map<?, ?>) cache).clear();
            log.info("Cleared cache '{}' with {} entries", cacheName, size);
            return true;
        }
        return false;
    }
    
    /**
     * Generates a consistent cache key from multiple parts.
     *
     * @param parts Parts to include in the cache key
     * @return Cache key string
     */
    public String generateCacheKey(String... parts) {
        return String.join(":", parts);
    }

    /**
     * Gets a registered cache by name.
     *
     * @param cacheName Name of the cache
     * @return The cache object, or an empty map if not found
     */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getCache(String cacheName) {
        Object cache = caches.get(cacheName);
        if (cache instanceof Map) {
            return (Map<K, V>) cache;
        }
        return new HashMap<>();
    }

    /**
     * Creates and registers a new concurrent cache.
     *
     * @param cacheName Name of the cache
     * @return The newly created cache
     */
    public <K, V> Map<K, V> createCache(String cacheName) {
        Map<K, V> newCache = new ConcurrentHashMap<>();
        caches.put(cacheName, newCache);
        log.debug("Created new cache: {}", cacheName);
        return newCache;
    }
}