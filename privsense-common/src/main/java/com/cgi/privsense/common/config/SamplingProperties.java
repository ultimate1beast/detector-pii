package com.cgi.privsense.common.config;

import lombok.Data;
import java.util.concurrent.TimeUnit;

/**
 * sampling properties with pagination, reservoir sampling,
 * dynamic timeouts, and caching support.
 */
@Data
public class SamplingProperties {
    /**
     * Timeout for sampling operations.
     */
    private long timeout = 60000;

    /**
     * Timeout unit for sampling operations.
     */
    private TimeUnit timeoutUnit = TimeUnit.MILLISECONDS;

    /**
     * Maximum timeout for any sampling operation.
     */
    private long maxTimeout = 300000; // 5 minutes

    /**
     * Page size for paginated sampling.
     */
    private int pageSize = 1000;

    /**
     * Maximum number of pages to fetch for sampling operations.
     */
    private int maxPages = 50;

    /**
     * Timeout factor per thousand rows (milliseconds).
     * Used for calculating dynamic timeouts based on table size.
     */
    private long timeoutFactorPerThousandRows = 500;

    /**
     * Whether to use reservoir sampling for large tables.
     */
    private boolean useReservoirSampling = true;

    /**
     * Threshold for reservoir sampling activation.
     */
    private int reservoirSamplingThreshold = 10000;

    /**
     * Whether to cache metadata for sampling.
     */
    private boolean cacheMetadata = true;

    /**
     * Metadata cache TTL in minutes.
     */
    private int metadataCacheTtl = 60;

    /**
     * Maximum number of concurrent operations.
     */
    private int maxConcurrentOperations = 8;
}