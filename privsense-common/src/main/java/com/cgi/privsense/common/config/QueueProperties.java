package com.cgi.privsense.common.config;

import lombok.Data;

import java.util.concurrent.TimeUnit;

/**
 * Queue configuration properties.
 */
@Data
public class QueueProperties {
    /**
     * Capacity of the queue.
     */
    private int capacity = 1000;

    /**
     * Batch size for processing.
     */
    private int batchSize = 20;

    /**
     * Timeout for polling the queue.
     */
    private long pollTimeout = 500;

    /**
     * Timeout unit for polling the queue.
     */
    private TimeUnit pollTimeoutUnit = TimeUnit.MILLISECONDS;
}