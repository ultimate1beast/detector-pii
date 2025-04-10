package com.cgi.privsense.dbscanner.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for caching with Caffeine.
 * Enables statistics recording to address cache metrics warnings.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configures the cache manager with proper statistics recording.
     *
     * @return The cache manager
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configure Caffeine to record statistics
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(1000)
            .recordStats(); // Enable statistics recording
            
        cacheManager.setCaffeine(caffeine);
        
        // Register all cache names used in the application
        cacheManager.setCacheNames(Arrays.asList(
            "tableMetadata", "columnMetadata", "columnResults", 
            "relationshipMetadata", "piiResults", "tableResults"
        ));
        
        return cacheManager;
    }
}