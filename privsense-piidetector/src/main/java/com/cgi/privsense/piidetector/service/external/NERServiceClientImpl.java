package com.cgi.privsense.piidetector.service.external;

import com.cgi.privsense.common.config.properties.PiiDetectionProperties;
import com.cgi.privsense.piidetector.api.NERServiceClient;
import com.cgi.privsense.piidetector.service.cache.NERResultsCache;
import com.cgi.privsense.piidetector.service.fallback.FallbackPIIDetector;
import com.cgi.privsense.piidetector.service.resilience.CircuitBreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Implementation of the NER service client interface.
 * Communicates with an external Named Entity Recognition service for PII detection.
 */
@Service
public class NERServiceClientImpl implements NERServiceClient {
    private static final Logger log = LoggerFactory.getLogger(NERServiceClientImpl.class);

    // Service configuration
    private final String nerServiceUrl;
    private final String backupNerServiceUrl;
    private final RestTemplate restTemplate;
    private final boolean isLocalService;
    private final int maxRetries;
    private final long retryDelayMs;
    
    // Components for resilience, caching, and fallback
    private final CircuitBreaker circuitBreaker;
    private final NERResultsCache resultsCache;
    private final FallbackPIIDetector fallbackDetector;

    public NERServiceClientImpl(
            PiiDetectionProperties piiDetectionProperties,
            NERResultsCache resultsCache,
            FallbackPIIDetector fallbackDetector) {

        this.nerServiceUrl = piiDetectionProperties.getNerService().getUrl();
        this.backupNerServiceUrl = piiDetectionProperties.getNerService().getBackupUrl();
        boolean trustAllCerts = piiDetectionProperties.getNerService().isTrustAllCerts();
        this.maxRetries = piiDetectionProperties.getNerService().getMaxRetries();
        this.retryDelayMs = piiDetectionProperties.getNerService().getRetryDelayMs();
        this.isLocalService = isLocalService(nerServiceUrl);
        
        // Initialize circuit breaker (3 failures, 1 minute timeout)
        this.circuitBreaker = new CircuitBreaker(3, 60000);
        
        // Initialize caching and fallback components
        this.resultsCache = resultsCache;
        this.fallbackDetector = fallbackDetector;

        // Create an appropriate RestTemplate based on whether this is a local service
        if (isLocalService && !trustAllCerts) {
            // Standard RestTemplate for local services
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(5000); // 5 seconds
            requestFactory.setReadTimeout(30000); // 30 seconds
            this.restTemplate = new RestTemplate(requestFactory);
            log.info("Using standard RestTemplate for local service at: {}", nerServiceUrl);
        } else {
            // Trust all RestTemplate for non-local services or if explicitly requested
            this.restTemplate = createStandardRestTemplate();
            log.info("Using standard RestTemplate with normal SSL handling for: {}", nerServiceUrl);
        }

        log.info("NER Service Client initialized with primary URL: {}", nerServiceUrl);
        if (!backupNerServiceUrl.isEmpty()) {
            log.info("Backup NER service configured: {}", backupNerServiceUrl);
        }
    }

    @Override
    public Map<String, Map<String, Double>> batchAnalyzeText(Map<String, List<String>> columnDataMap) {
        if (columnDataMap == null || columnDataMap.isEmpty()) {
            log.warn("No columns to analyze");
            return Collections.emptyMap();
        }

        // Check if circuit breaker is open
        if (circuitBreaker.isOpen()) {
            log.warn("Circuit breaker is open. Using fallback patterns for NER detection.");
            return fallbackDetector.batchDetectPII(columnDataMap);
        }

        // Prepare batch data
        BatchProcessingData batchData = prepareBatchData(columnDataMap);

        // If all results were from cache or no samples to process
        if (batchData.allSamples.isEmpty()) {
            return batchData.results;
        }

        // Process the batch with retries
        return processBatchWithRetries(columnDataMap, batchData);
    }

    @Override
    public Map<String, Double> analyzeText(List<String> textSamples) {
        if (textSamples == null || textSamples.isEmpty()) {
            log.warn("No text to analyze");
            return Collections.emptyMap();
        }

        // Check if circuit breaker is open
        if (circuitBreaker.isOpen()) {
            log.warn("Circuit breaker is open. Using fallback patterns for single sample NER detection.");
            return fallbackDetector.detectPII(textSamples);
        }

        // Check cache first
        Map<String, Double> cachedResult = resultsCache.getResults(textSamples);
        if (cachedResult != null) {
            return cachedResult;
        }

        return processSingleAnalysisWithRetries(textSamples);
    }

    @Override
    public boolean isServiceAvailable() {
        // If circuit is open, try to see if it can be reset
        if (circuitBreaker.isOpen()) {
            return false;
        }

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    nerServiceUrl.replace("/ner", "/health"),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                circuitBreaker.recordSuccess();
                return true;
            } else {
                circuitBreaker.recordFailure();
                return false;
            }
        } catch (Exception e) {
            log.warn("NER service not available: {}", e.getMessage());
            circuitBreaker.recordFailure();

            // If primary service is down, try backup if configured
            if (!backupNerServiceUrl.isEmpty()) {
                try {
                    ResponseEntity<String> backupResponse = restTemplate.getForEntity(
                            backupNerServiceUrl.replace("/ner", "/health"),
                            String.class);

                    if (backupResponse.getStatusCode().is2xxSuccessful()) {
                        log.info("Backup NER service is available");
                        return true;
                    }
                } catch (Exception ex) {
                    log.warn("Backup NER service also not available: {}", ex.getMessage());
                }
            }

            return false;
        }
    }

    @Override
    public void clearCache() {
        resultsCache.clearCache();
    }

    /**
     * For testing purposes
     */
    public void resetCircuitBreakerForTesting() {
        circuitBreaker.forceReset();
        log.info("Circuit breaker manually reset");
    }

    /*
     * Private helper methods
     */

    /**
     * Data structure to hold batch processing information.
     */
    private static class BatchProcessingData {
        final Map<String, Map<String, Double>> results = new HashMap<>();
        final List<String> allSamples = new ArrayList<>();
        final Map<String, Integer> sampleCounts = new HashMap<>();
        final Map<String, Integer> sampleStartIndices = new HashMap<>();
    }

    /**
     * Prepares data for batch processing, organizing samples and checking cache.
     */
    private BatchProcessingData prepareBatchData(Map<String, List<String>> columnDataMap) {
        BatchProcessingData data = new BatchProcessingData();
        int currentIndex = 0;

        // Prepare all samples for batch processing
        for (Map.Entry<String, List<String>> entry : columnDataMap.entrySet()) {
            String columnName = entry.getKey();
            List<String> samples = entry.getValue();

            if (samples == null || samples.isEmpty()) {
                data.results.put(columnName, Collections.emptyMap());
            } else {
                // Check cache first
                Map<String, Double> cachedResult = resultsCache.getColumnResults(columnName, samples);
                if (cachedResult != null) {
                    data.results.put(columnName, cachedResult);
                } else {
                    // Process samples that are not empty and not in cache
                    data.sampleStartIndices.put(columnName, currentIndex);
                    data.sampleCounts.put(columnName, samples.size());
                    data.allSamples.addAll(samples);
                    currentIndex += samples.size();
                }
            }
        }

        return data;
    }

    /**
     * Processes a batch of samples with retry logic.
     */
    private Map<String, Map<String, Double>> processBatchWithRetries(
            Map<String, List<String>> columnDataMap, BatchProcessingData batchData) {

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                log.info("Retry attempt {} for NER service", attempt);
                performRetryDelay();
            }

            String currentUrl = selectServiceUrl(attempt);

            try {
                Map<String, List<Map<String, Double>>> batchResults = sendBatchRequest(currentUrl,
                        batchData.allSamples);

                if (batchResults != null) {
                    processBatchResults(columnDataMap, batchData, batchResults);
                    return batchData.results;
                }

                if (attempt >= maxRetries) {
                    circuitBreaker.recordFailure();
                    return fallbackDetector.batchDetectPII(columnDataMap);
                }
                circuitBreaker.recordFailure();
            } catch (ResourceAccessException e) {
                log.error("Resource access exception calling NER service: {}", e.getMessage());
                circuitBreaker.recordFailure();
                if (attempt >= maxRetries || circuitBreaker.isOpen()) {
                    return fallbackDetector.batchDetectPII(columnDataMap);
                }
            } catch (Exception e) {
                log.error("Exception when calling NER service: {}", e.getMessage(), e);
                circuitBreaker.recordFailure();
                if (attempt >= maxRetries) {
                    return fallbackDetector.batchDetectPII(columnDataMap);
                }
            }
        }

        return fallbackDetector.batchDetectPII(columnDataMap);
    }

    /**
     * Processes a single analysis request with retry logic.
     */
    private Map<String, Double> processSingleAnalysisWithRetries(List<String> textSamples) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                log.info("Retry attempt {} for NER service", attempt);
                performRetryDelay();
            }

            try {
                // Determine which URL to use (primary or backup)
                String currentUrl = selectServiceUrl(attempt);

                // Send the analysis request
                Map<String, Double> result = sendSingleAnalysisRequest(currentUrl, textSamples);

                if (result != null) {
                    // Cache the results
                    resultsCache.cacheResults(null, textSamples, result);
                    return result;
                } else {
                    circuitBreaker.recordFailure();
                    if (attempt >= maxRetries) {
                        return fallbackDetector.detectPII(textSamples);
                    }
                }
            } catch (Exception e) {
                if (e instanceof ResourceAccessException) {
                    log.error("Resource access exception calling NER service: {}", e.getMessage());
                } else {
                    log.error("Exception when calling NER service: {}", e.getMessage(), e);
                }
                
                circuitBreaker.recordFailure();
                if (attempt >= maxRetries || circuitBreaker.isOpen()) {
                    return fallbackDetector.detectPII(textSamples);
                }
            }
        }

        // If we reach here, all attempts failed
        return fallbackDetector.detectPII(textSamples);
    }

    /**
     * Selects the appropriate service URL based on the current attempt.
     */
    private String selectServiceUrl(int attempt) {
        return attempt > 0 && !backupNerServiceUrl.isEmpty() ? backupNerServiceUrl : nerServiceUrl;
    }

    /**
     * Adds a delay between retry attempts.
     */
    private void performRetryDelay() {
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends a batch request to the NER service.
     */
    private Map<String, List<Map<String, Double>>> sendBatchRequest(String url, List<String> samples) {
        // Prepare the batch request
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("texts", samples);

        log.debug("Sending {} text samples to NER service in batch mode", samples.size());

        // Send the request to the NER service
        ResponseEntity<Map<String, List<Map<String, Double>>>> response = restTemplate.exchange(
                url + "/batch",
                HttpMethod.POST,
                new HttpEntity<>(requestBody),
                new ParameterizedTypeReference<Map<String, List<Map<String, Double>>>>() {
                });

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            // Reset failure counter on success
            circuitBreaker.recordSuccess();
            return response.getBody();
        }

        log.warn("NER service returned unsuccessful status code: {}", response.getStatusCode());
        return null;
    }

    /**
     * Sends a single analysis request to the NER service.
     */
    private Map<String, Double> sendSingleAnalysisRequest(String url, List<String> textSamples) {
        // Prepare the request
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("texts", textSamples);

        log.debug("Sending {} text samples to NER service", textSamples.size());

        // Send the request to the NER service
        ResponseEntity<Map<String, Double>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(requestBody),
                new ParameterizedTypeReference<Map<String, Double>>() {
                });

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            log.debug("NER results received: {}", response.getBody());
            circuitBreaker.recordSuccess();
            return response.getBody();
        }

        return null;
    }

    /**
     * Processes results from a batch request.
     */
    private void processBatchResults(Map<String, List<String>> columnDataMap,
            BatchProcessingData batchData,
            Map<String, List<Map<String, Double>>> batchResults) {
        
        if (batchResults != null && batchResults.containsKey("results")) {
            List<Map<String, Double>> resultsList = batchResults.get("results");

            if (resultsList != null) {
                // Process results for each column
                for (Map.Entry<String, Integer> entry : batchData.sampleCounts.entrySet()) {
                    String columnName = entry.getKey();
                    int count = entry.getValue();
                    int startIndex = batchData.sampleStartIndices.get(columnName);

                    if (startIndex + count <= resultsList.size()) {
                        // Extract and aggregate results for this column
                        Map<String, Double> columnResults = aggregateColumnResults(
                                resultsList.subList(startIndex, startIndex + count));

                        batchData.results.put(columnName, columnResults);

                        // Cache the results
                        resultsCache.cacheResults(columnName, columnDataMap.get(columnName), columnResults);
                    } else {
                        log.error("Index out of bounds when processing batch results for column: {}", columnName);
                        batchData.results.put(columnName, Collections.emptyMap());
                    }
                }
            }
        }
    }

    /**
     * Aggregates individual sample results for a column into a single confidence map.
     */
    private Map<String, Double> aggregateColumnResults(List<Map<String, Double>> sampleResults) {
        Map<String, List<Double>> entityConfidences = new HashMap<>();

        // Collect all confidences for each entity type
        for (Map<String, Double> result : sampleResults) {
            for (Map.Entry<String, Double> entity : result.entrySet()) {
                entityConfidences
                        .computeIfAbsent(entity.getKey(), k -> new ArrayList<>())
                        .add(entity.getValue());
            }
        }

        // Calculate average confidence for each entity type
        Map<String, Double> aggregatedResults = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : entityConfidences.entrySet()) {
            // Use 75th percentile as confidence to reduce impact of outliers
            List<Double> confidences = entry.getValue();
            Collections.sort(confidences);
            int p75Index = (int) (confidences.size() * 0.75);
            Double confidenceValue = confidences.get(Math.min(p75Index, confidences.size() - 1));

            aggregatedResults.put(entry.getKey(), confidenceValue);
        }

        return aggregatedResults;
    }

    /**
     * Creates a standard RestTemplate with reasonable timeouts.
     */
    private RestTemplate createStandardRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000); // 5 seconds
        requestFactory.setReadTimeout(30000); // 30 seconds
        return new RestTemplate(requestFactory);
    }

    /**
     * Determine if the service URL is local
     */
    private boolean isLocalService(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host == null || host.equals("localhost") || host.equals("127.0.0.1") || host.startsWith("192.168.")
                    || host.startsWith("10.");
        } catch (URISyntaxException e) {
            log.warn("Invalid URI syntax for service URL: {}", url);
            return false;
        }
    }
}