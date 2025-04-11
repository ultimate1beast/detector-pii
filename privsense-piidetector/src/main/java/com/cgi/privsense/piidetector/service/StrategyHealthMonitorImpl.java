package com.cgi.privsense.piidetector.service;

import com.cgi.privsense.piidetector.api.StrategyHealthMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of StrategyHealthMonitor.
 * Monitors and manages the health of PII detection strategies.
 * Implements circuit breaker pattern to prevent cascading failures.
 */
@Component
public class StrategyHealthMonitorImpl implements StrategyHealthMonitor {
    private static final Logger log = LoggerFactory.getLogger(StrategyHealthMonitorImpl.class);

    // Strategy state tracking for error recovery
    private final Map<String, Boolean> strategyHealth = new ConcurrentHashMap<>();
    private final Map<String, Integer> strategyFailureCount = new ConcurrentHashMap<>();
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long HEALTH_CHECK_INTERVAL_MS = 300000; // 5 minutes
    private final Map<String, Long> lastHealthCheckTime = new ConcurrentHashMap<>();
    private final AtomicBoolean emergencyModeActive = new AtomicBoolean(false);

    /**
     * Initializes the health status for all strategies.
     */
    @Override
    public void initializeStrategyHealth(String... strategyNames) {
        for (String name : strategyNames) {
            strategyHealth.put(name, true);
            strategyFailureCount.put(name, 0);
            lastHealthCheckTime.put(name, System.currentTimeMillis());
        }
        log.info("Initialized health monitoring for {} strategies", strategyNames.length);
    }

    /**
     * Marks a strategy as healthy after successful execution.
     * 
     * @param strategyName Name of the strategy
     */
    @Override
    public void markStrategySuccess(String strategyName) {
        strategyHealth.put(strategyName, true);
        strategyFailureCount.put(strategyName, 0);
    }

    /**
     * Records a strategy failure and marks it as unhealthy if threshold is reached.
     * 
     * @param strategyName Name of the strategy
     */
    @Override
    public synchronized void markStrategyFailure(String strategyName) {
        int failures = strategyFailureCount.getOrDefault(strategyName, 0) + 1;
        strategyFailureCount.put(strategyName, failures);

        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            // Convert to primitive boolean to address the S5411 warning
            boolean isCurrentlyHealthy = strategyHealth.getOrDefault(strategyName, true);
            if (isCurrentlyHealthy) {
                log.warn("Strategy '{}' marked unhealthy after {} consecutive failures",
                        strategyName, failures);
                strategyHealth.put(strategyName, false);

                // Check if we should enter emergency mode (multiple strategies failing)
                int unhealthyCount = 0;
                // Use primitive boolean in the loop
                for (boolean healthy : strategyHealth.values()) {
                    if (!healthy)
                        unhealthyCount++;
                }

                if (unhealthyCount >= 2 && !emergencyModeActive.get()) {
                    log.error("Multiple detection strategies ({}) are unhealthy, activating emergency mode",
                            unhealthyCount);
                    emergencyModeActive.set(true);
                }
            }
        }
    }

    /**
     * Checks if a strategy is considered healthy.
     * 
     * @param strategyName Name of the strategy
     * @return true if strategy is healthy
     */
    @Override
    public boolean isStrategyHealthy(String strategyName) {
        return strategyHealth.getOrDefault(strategyName, true);
    }

    /**
     * Checks if emergency mode is active.
     *
     * @return true if emergency mode is active
     */
    @Override
    public boolean isEmergencyModeActive() {
        return emergencyModeActive.get();
    }

    /**
     * Periodically checks unhealthy strategies and attempts to recover them.
     * 
     * @param recoveryAction Function to check if NER service is available
     */
    @Override
    public synchronized void checkAndRecoverStrategies(Runnable recoveryAction) {
        long currentTime = System.currentTimeMillis();

        // Process each strategy
        strategyHealth.entrySet().stream()
                .filter(entry -> !entry.getValue()) // Only process unhealthy strategies
                .forEach(entry -> attemptStrategyRecovery(entry.getKey(), currentTime, recoveryAction));

        // Check if we can exit emergency mode
        updateEmergencyModeStatus();
    }

    /**
     * Attempts to recover a single unhealthy strategy
     */
    private void attemptStrategyRecovery(String strategyName, long currentTime, Runnable recoveryAction) {
        // Check if we should attempt recovery
        long lastCheck = lastHealthCheckTime.getOrDefault(strategyName, 0L);
        if (currentTime - lastCheck <= HEALTH_CHECK_INTERVAL_MS) {
            return;
        }

        log.info("Attempting recovery of unhealthy strategy: {}", strategyName);
        lastHealthCheckTime.put(strategyName, currentTime);

        try {
            if ("heuristic".equals(strategyName) || "regex".equals(strategyName)) {
                // Heuristic and regex strategies are local and should recover
                markStrategyHealthy(strategyName);
            } else if ("ner".equals(strategyName) && recoveryAction != null) {
                // Execute recovery action which should check NER service availability
                recoveryAction.run();
                markStrategyHealthy(strategyName);
            }
        } catch (Exception e) {
            log.warn("Failed to recover strategy {}: {}", strategyName, e.getMessage());
        }
    }

    /**
     * Marks a strategy as healthy and resets its failure count
     */
    private void markStrategyHealthy(String strategyName) {
        strategyHealth.put(strategyName, true);
        strategyFailureCount.put(strategyName, 0);
        log.info("Successfully recovered {} strategy", strategyName);
    }

    /**
     * Updates the emergency mode status based on current strategy health
     */
    private void updateEmergencyModeStatus() {
        if (!emergencyModeActive.get()) {
            return;
        }

        // Count unhealthy strategies
        long unhealthyCount = strategyHealth.values().stream()
                .filter(healthy -> !healthy)
                .count();

        if (unhealthyCount < 2) {
            log.info("Exiting emergency mode as strategies have recovered");
            emergencyModeActive.set(false);
        }
    }

    /**
     * Forces a health check on all strategies and attempts recovery.
     * Used for manual intervention when issues are detected.
     * 
     * @param recoveryAction Function to execute for recovery attempt
     * @return Map of strategy health statuses
     */
    @Override
    public Map<String, Boolean> forceHealthCheck(Runnable recoveryAction) {
        log.info("Forcing health check on all detection strategies");

        for (String strategyName : strategyHealth.keySet()) {
            lastHealthCheckTime.put(strategyName, 0L); // Force immediate check
        }

        checkAndRecoverStrategies(recoveryAction);
        return new HashMap<>(strategyHealth);
    }

    /**
     * Resets the emergency mode flag.
     * Used for manual intervention when issues are resolved.
     */
    @Override
    public void resetEmergencyMode() {
        if (emergencyModeActive.get()) {
            log.info("Manually resetting emergency mode");
            emergencyModeActive.set(false);
        }
    }

    /**
     * Gets the current health status of all strategies.
     * 
     * @return Map of strategy health statuses
     */
    @Override
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("strategies", new HashMap<>(strategyHealth));
        status.put("emergencyMode", emergencyModeActive.get());
        return status;
    }
}