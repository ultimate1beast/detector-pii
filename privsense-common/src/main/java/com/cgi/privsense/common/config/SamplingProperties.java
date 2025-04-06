package com.cgi.privsense.common.config;

import lombok.Data;
import java.util.concurrent.TimeUnit;

/**
 * Sampling configuration properties.
 */
@Data
public class SamplingProperties {
    /**
     * Timeout for sampling operations.
     */
    private long timeout = 30;

    /**
     * Timeout unit for sampling operations.
     */
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    /**
     * Whether to use the queue for sampling.
     */
    private boolean useQueue = true;

    /**
     * Default sampling size.
     */
    private int defaultSampleSize = 100;

    /**
     * Maximum allowed sample size.
     */
    private int maxSampleSize = 10000;
}