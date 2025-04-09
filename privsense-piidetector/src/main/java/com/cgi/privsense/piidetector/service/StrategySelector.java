package com.cgi.privsense.piidetector.service;

import com.cgi.privsense.piidetector.api.PIIDetectionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Service responsible for selecting appropriate strategies for PII detection
 * based on context and available strategies.
 * Implements the Strategy pattern for runtime strategy selection.
 */
@Service
public class StrategySelector {
    private static final Logger log = LoggerFactory.getLogger(StrategySelector.class);
    
    private final Map<String, Boolean> activeStrategies = new ConcurrentHashMap<>();
    private final StrategyHealthMonitor healthMonitor;
    
    public StrategySelector(StrategyHealthMonitor healthMonitor) {
        this.healthMonitor = healthMonitor;
    }
    
    /**
     * Configures which strategies should be active/inactive.
     * 
     * @param strategyMap Map of strategy names to active state
     */
    public void configureStrategies(Map<String, Boolean> strategyMap) {
        activeStrategies.putAll(strategyMap);
        log.info("Strategy configuration updated: {}", strategyMap);
    }
    
    /**
     * Filters a list of strategies based on their health, activation status,
     * and context requirements.
     * 
     * @param strategies All available strategies
     * @param hasMetadata Whether metadata is available
     * @param hasSampleData Whether sample data is available
     * @param emergencyMode Whether we're in emergency mode
     * @return Filtered list of applicable strategies
     */
    public List<PIIDetectionStrategy> selectStrategies(
            List<PIIDetectionStrategy> strategies, 
            boolean hasMetadata, 
            boolean hasSampleData, 
            boolean emergencyMode) {
        
        return strategies.stream()
                .filter(strategy -> isStrategyActive(strategy.getName()))
                .filter(strategy -> !emergencyMode || isLightweightStrategy(strategy.getName()))
                .filter(strategy -> strategy.isApplicable(hasMetadata, hasSampleData))
                .filter(strategy -> healthMonitor.isStrategyHealthy(strategy.getName()))
                .toList();
    }
    
    /**
     * Determines if a specific strategy is active.
     * 
     * @param strategyName Name of the strategy
     * @return True if active, false otherwise
     */
    public boolean isStrategyActive(String strategyName) {
        return activeStrategies.getOrDefault(strategyName, true);
    }
    
    /**
     * Determines if a strategy is considered "lightweight" and can be run in emergency mode.
     * 
     * @param strategyName Name of the strategy
     * @return True if the strategy is lightweight
     */
    private boolean isLightweightStrategy(String strategyName) {
        // Heuristic and regex strategies are considered lightweight
        return strategyName.contains("Heuristic") || strategyName.contains("Regex");
    }
    
    /**
     * Selects the appropriate first-pass strategy based on context.
     * 
     * @param hasMetadata Whether metadata is available
     * @param hasSampleData Whether sample data is available
     * @param emergencyMode Whether we're in emergency mode
     * @return Strategy name to use
     */
    public String selectFirstPassStrategy(boolean hasMetadata, boolean hasSampleData, boolean emergencyMode) {
        if (hasMetadata && healthMonitor.isStrategyHealthy("heuristic")) {
            return "heuristic";
        } else if (hasSampleData && healthMonitor.isStrategyHealthy("regex") && !emergencyMode) {
            return "regex";
        } else {
            return "none";
        }
    }
    
    /**
     * Gets all active strategies as a map.
     * 
     * @return Map of strategy names to active status
     */
    public Map<String, Boolean> getActiveStrategies() {
        return new ConcurrentHashMap<>(activeStrategies);
    }
    
    /**
     * Clears the strategy selection cache.
     */
    public void clearCache() {
        // Currently, no caching is implemented,
        // but this method provides an extension point for future caching
    }
}