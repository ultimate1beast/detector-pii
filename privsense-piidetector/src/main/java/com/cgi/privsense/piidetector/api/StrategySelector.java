package com.cgi.privsense.piidetector.api;

import java.util.List;
import java.util.Map;

/**
 * Interface for selecting and configuring PII detection strategies.
 * Responsible for determining which strategies to apply in different scenarios.
 */
public interface StrategySelector {
    
    /**
     * Selects the appropriate strategies for a given column based on its characteristics.
     * 
     * @param columnName Column name
     * @param columnType Database column type
     * @param hasSamples Whether sample data is available
     * @return List of strategy names to apply
     */
    List<String> selectStrategiesForColumn(String columnName, String columnType, boolean hasSamples);
    
    /**
     * Updates the active status of specific strategies.
     * 
     * @param strategyMap Map of strategy names to active status
     */
    void configureActiveStrategies(Map<String, Boolean> strategyMap);
    
    /**
     * Checks if a strategy is active and should be used.
     * 
     * @param strategyName Name of the strategy to check
     * @return true if the strategy is active
     */
    boolean isStrategyActive(String strategyName);
    
    /**
     * Gets all currently active strategies.
     * 
     * @return Map of strategy names to active status
     */
    Map<String, Boolean> getActiveStrategies();
}