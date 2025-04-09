/*
 * PIIDetectionStrategyFactoryImpl.java - Factory implementation for detection strategies
 */
package com.cgi.privsense.piidetector.service;

import com.cgi.privsense.piidetector.api.PIIDetectionStrategy;
import com.cgi.privsense.piidetector.api.PIIDetectionStrategyFactory;
import com.cgi.privsense.piidetector.strategy.CompositePIIDetectionStrategy;
import com.cgi.privsense.piidetector.strategy.HeuristicNameStrategy;
import com.cgi.privsense.piidetector.strategy.NERModelStrategy;
import com.cgi.privsense.piidetector.strategy.RegexPatternStrategy;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating PII detection strategies.
 * Implements the Factory pattern.
 */
@Component
public class PIIDetectionStrategyFactoryImpl implements PIIDetectionStrategyFactory {
    private static final Logger log = LoggerFactory.getLogger(PIIDetectionStrategyFactoryImpl.class);

    private final Map<String, PIIDetectionStrategy> strategies = new ConcurrentHashMap<>();
    private final PIIDetectionCacheManager cacheManager;
    private final DetectionResultFactory resultFactory;

    // Cache for composite strategies
    private Map<String, PIIDetectionStrategy> compositeStrategiesCache;

    public PIIDetectionStrategyFactoryImpl(
            HeuristicNameStrategy heuristicStrategy,
            RegexPatternStrategy regexStrategy,
            NERModelStrategy nerStrategy,
            CompositePIIDetectionStrategy compositeStrategy,
            PIIDetectionCacheManager cacheManager,
            DetectionResultFactory resultFactory) {

        this.cacheManager = cacheManager;
        this.resultFactory = resultFactory;

        // Register available strategies
        strategies.put(heuristicStrategy.getName(), heuristicStrategy);
        strategies.put(regexStrategy.getName(), regexStrategy);
        strategies.put(nerStrategy.getName(), nerStrategy);
        strategies.put(compositeStrategy.getName(), compositeStrategy);

        log.info("PII detection strategy factory initialized with {} strategies", strategies.size());
    }

    @PostConstruct
    public void init() {
        // Create and register the composite strategies cache
        compositeStrategiesCache = cacheManager.createCache("compositeStrategiesCache");

        // Register the strategies cache
        cacheManager.registerCache("strategies", strategies);

        // Set cache manager and result factory in all strategies
        for (PIIDetectionStrategy strategy : strategies.values()) {
            if (strategy instanceof CompositePIIDetectionStrategy compositePiiDetectionStrategy) {
                compositePiiDetectionStrategy.setCacheManager(cacheManager);
                compositePiiDetectionStrategy.setResultFactory(resultFactory);
            }
        }
    }

    @Override
    public PIIDetectionStrategy createStrategy(String strategyName) {
        PIIDetectionStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown strategy: " + strategyName);
        }
        return strategy;
    }

    @Override
    public List<PIIDetectionStrategy> getAllStrategies() {
        return new ArrayList<>(strategies.values());
    }

    @Override
    public PIIDetectionStrategy createCompositeStrategy(List<String> strategyNames) {
        // Sort strategy names to ensure consistent cache keys
        List<String> sortedNames = new ArrayList<>(strategyNames);
        Collections.sort(sortedNames);

        // Create cache key
        String cacheKey = cacheManager.generateCacheKey(sortedNames.toArray(new String[0]));

        // Check cache first
        if (compositeStrategiesCache.containsKey(cacheKey)) {
            return compositeStrategiesCache.get(cacheKey);
        }

        // Create new composite strategy
        CompositePIIDetectionStrategy composite = new CompositePIIDetectionStrategy();

        // Set cache manager and result factory
        composite.setCacheManager(cacheManager);
        composite.setResultFactory(resultFactory);

        for (String name : sortedNames) {
            PIIDetectionStrategy strategy = createStrategy(name);
            composite.addStrategy(strategy);
        }

        // Cache the result
        compositeStrategiesCache.put(cacheKey, composite);

        return composite;
    }

    /**
     * Clears the composite strategies cache.
     */
    public void clearCompositeCache() {
        if (compositeStrategiesCache != null) {
            compositeStrategiesCache.clear();
        }
    }
}