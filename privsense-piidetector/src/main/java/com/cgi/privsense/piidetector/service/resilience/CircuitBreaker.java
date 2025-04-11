package com.cgi.privsense.piidetector.service.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of the Circuit Breaker pattern for enhancing resilience.
 * Prevents system overload during service failures and allows for recovery periods.
 */
public class CircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);
    
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private final AtomicLong circuitResetTime = new AtomicLong(0);
    private final int failureThreshold;
    private final long resetTimeoutMs;
    private int consecutiveFailures = 0;

    /**
     * Creates a new CircuitBreaker with the specified configuration.
     * 
     * @param failureThreshold Number of consecutive failures before opening the circuit
     * @param resetTimeoutMs Time in milliseconds before allowing a test request after opening
     */
    public CircuitBreaker(int failureThreshold, long resetTimeoutMs) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        log.debug("CircuitBreaker initialized with threshold={}, resetTimeout={}ms", 
                failureThreshold, resetTimeoutMs);
    }

    /**
     * Records a service failure and potentially opens the circuit breaker.
     * 
     * @return true if the circuit was opened as a result of this failure
     */
    public synchronized boolean recordFailure() {
        boolean wasOpened = false;
        consecutiveFailures++;
        log.debug("Service failure recorded: {} consecutive failures", consecutiveFailures);

        if (consecutiveFailures >= failureThreshold && !circuitOpen.get()) {
            log.warn("Circuit breaker threshold reached ({}), opening circuit", failureThreshold);
            circuitOpen.set(true);
            circuitResetTime.set(System.currentTimeMillis() + resetTimeoutMs);
            wasOpened = true;
        }
        
        return wasOpened;
    }

    /**
     * Resets the circuit breaker on successful operation.
     */
    public synchronized void recordSuccess() {
        if (consecutiveFailures > 0) {
            log.debug("Resetting circuit breaker failure counter");
            consecutiveFailures = 0;
        }

        if (circuitOpen.get()) {
            log.info("Closing circuit breaker after successful operation");
            circuitOpen.set(false);
        }
    }

    /**
     * Checks if the circuit breaker is currently open.
     * Includes half-open state logic for testing recovery.
     * 
     * @return true if circuit is open and reset timeout hasn't elapsed
     */
    public boolean isOpen() {
        // If circuit breaker timeout has elapsed, allow a test request (half-open state)
        if (circuitOpen.get() && System.currentTimeMillis() > circuitResetTime.get()) {
            log.info("Circuit breaker in half-open state, allowing test request");
            return false;
        }
        return circuitOpen.get();
    }

    /**
     * Forcibly resets the circuit breaker for testing or administrative purposes.
     */
    public synchronized void forceReset() {
        consecutiveFailures = 0;
        circuitOpen.set(false);
        log.info("Circuit breaker manually reset");
    }
    
    /**
     * Gets the current number of consecutive failures.
     * 
     * @return Number of consecutive failures
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
}