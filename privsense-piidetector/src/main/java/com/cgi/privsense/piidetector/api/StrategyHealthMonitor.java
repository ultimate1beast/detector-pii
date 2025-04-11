package com.cgi.privsense.piidetector.api;

import java.util.Map;

/**
 * Interface for monitoring and managing the health of PII detection strategies.
 * Implements circuit breaker pattern to prevent cascading failures.
 */
public interface StrategyHealthMonitor {
    
    /**
     * Initializes the health status for all strategies.
     *
     * @param strategyNames Names of strategies to monitor
     */
    void initializeStrategyHealth(String... strategyNames);

    /**
     * Marks a strategy as healthy after successful execution.
     * 
     * @param strategyName Name of the strategy
     */
    void markStrategySuccess(String strategyName);

    /**
     * Records a strategy failure and marks it as unhealthy if threshold is reached.
     * 
     * @param strategyName Name of the strategy
     */
    void markStrategyFailure(String strategyName);

    /**
     * Checks if a strategy is considered healthy.
     * 
     * @param strategyName Name of the strategy
     * @return true if strategy is healthy
     */
    boolean isStrategyHealthy(String strategyName);

    /**
     * Checks if emergency mode is active.
     *
     * @return true if emergency mode is active
     */
    boolean isEmergencyModeActive();

    /**
     * Periodically checks unhealthy strategies and attempts to recover them.
     * 
     * @param recoveryAction Function to execute for recovery attempt
     */
    void checkAndRecoverStrategies(Runnable recoveryAction);

    /**
     * Forces a health check on all strategies and attempts recovery.
     * 
     * @param recoveryAction Function to execute for recovery attempt
     * @return Map of strategy health statuses
     */
    Map<String, Boolean> forceHealthCheck(Runnable recoveryAction);

    /**
     * Resets the emergency mode flag.
     */
    void resetEmergencyMode();

    /**
     * Gets the current health status of all strategies.
     * 
     * @return Map of strategy health statuses
     */
    Map<String, Object> getHealthStatus();
}