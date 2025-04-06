package com.cgi.privsense.common.config;

import lombok.Data;

/**
 * Thread configuration properties.
 */
@Data
public class ThreadProperties {
    /**
     * Maximum pool size.
     */
    private int maxPoolSize = 8;

    /**
     * Sampler pool size.
     */
    private int samplerPoolSize = 4;

    /**
     * Number of sampling consumers.
     */
    private int samplingConsumers = 3;
}