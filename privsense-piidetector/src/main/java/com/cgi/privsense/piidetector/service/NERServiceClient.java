package com.cgi.privsense.piidetector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for the external NER (Named Entity Recognition) service.
 * Uses RestTemplate to communicate with the Python service.
 * Includes fallback mechanisms and circuit breaker pattern for resilience.
 */
@Component
public class NERServiceClient {
    private static final Logger log = LoggerFactory.getLogger(NERServiceClient.class);

    // Circuit breaker pattern variables
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private final AtomicLong circuitResetTime = new AtomicLong(0);
    private static final long CIRCUIT_RESET_TIMEOUT_MS = 60000; // 1 minute
    private static final int FAILURE_THRESHOLD = 3;
    private int consecutiveFailures = 0;

    // Service endpoints and connection parameters
    private final String nerServiceUrl;
    private final String backupNerServiceUrl;
    private final RestTemplate restTemplate;
    private final boolean isLocalService;
    private final int maxRetries;
    private final long retryDelayMs;

    // Cache for NER results to avoid redundant calls
    private final Map<String, Map<String, Double>> nerResultsCache = new ConcurrentHashMap<>();

    // Minimal fallback patterns for critical PII types when NER service is down
    private static final Map<String, Pattern> FALLBACK_PATTERNS = new HashMap<>();
    static {
        // Email pattern
        FALLBACK_PATTERNS.put("EMAIL", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}"));
        // Phone pattern (simple international format)
        FALLBACK_PATTERNS.put("PHONE", Pattern.compile("\\+?\\d{10,15}"));
        // SSN pattern (US format)
        FALLBACK_PATTERNS.put("SSN", Pattern.compile("\\d{3}-\\d{2}-\\d{4}"));
        // Credit card pattern (simplified)
        FALLBACK_PATTERNS.put("CREDIT_CARD", Pattern.compile("\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}"));
    }

    /**
     * Constructor
     *
     * @param nerServiceUrl       NER service URL
     * @param backupNerServiceUrl Backup NER service URL (optional)
     * @param trustAllCerts       Whether to trust all certificates (default: false)
     * @param maxRetries          Maximum number of retries for failed requests
     * @param retryDelayMs        Delay between retries in milliseconds
     */
    public NERServiceClient(
            @Value("${piidetector.ner.service.url}") String nerServiceUrl,
            @Value("${piidetector.ner.service.backup.url:}") String backupNerServiceUrl,
            @Value("${piidetector.ner.trust.all.certs:false}") boolean trustAllCerts,
            @Value("${piidetector.ner.max.retries:3}") int maxRetries,
            @Value("${piidetector.ner.retry.delay.ms:1000}") long retryDelayMs) {

        this.nerServiceUrl = nerServiceUrl;
        this.backupNerServiceUrl = backupNerServiceUrl;
        this.isLocalService = isLocalService(nerServiceUrl);
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;

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

    /**
     * Determine if the service URL is local
     *
     * @param url The service URL
     * @return true if local, false otherwise
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

    /**
     * Batch analysis of text samples with the NER service.
     * Processes multiple columns in a single API call to reduce network overhead.
     *
     * @param columnDataMap Map of column names to text samples
     * @return Map of column names to detected entity types with confidence levels
     */
    public Map<String, Map<String, Double>> batchAnalyzeText(Map<String, List<String>> columnDataMap) {
        if (columnDataMap == null || columnDataMap.isEmpty()) {
            log.warn("No columns to analyze");
            return Collections.emptyMap();
        }

        // Check if circuit breaker is open
        if (isCircuitOpen()) {
            log.warn("Circuit breaker is open. Using fallback patterns for NER detection.");
            return applyFallbackDetection(columnDataMap);
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
     *
     * @param columnDataMap Map of column names to text samples
     * @return BatchProcessingData with organized samples and cached results
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
                String cacheKey = columnName + "_" + String.join("_", samples);
                if (nerResultsCache.containsKey(cacheKey)) {
                    data.results.put(columnName, nerResultsCache.get(cacheKey));
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
     *
     * @param columnDataMap Original map of column data
     * @param batchData     Prepared batch processing data
     * @return Map of column names to detection results
     */
    private Map<String, Map<String, Double>> processBatchWithRetries(
            Map<String, List<String>> columnDataMap, BatchProcessingData batchData) {

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            prepareForAttempt(attempt);

            String currentUrl = selectServiceUrl(attempt);

            try {
                Map<String, List<Map<String, Double>>> batchResults = sendBatchRequest(currentUrl,
                        batchData.allSamples);

                if (batchResults != null) {
                    processBatchResults(columnDataMap, batchData, batchResults);
                    return batchData.results;
                }

                if (handleFailedAttempt(attempt)) {
                    return applyFallbackDetection(columnDataMap);
                }
            } catch (ResourceAccessException e) {
                if (handleResourceException(e, attempt)) {
                    return applyFallbackDetection(columnDataMap);
                }
            } catch (Exception e) {
                if (handleGeneralException(e, attempt)) {
                    return applyFallbackDetection(columnDataMap);
                }
            }
        }

        return applyFallbackDetection(columnDataMap);
    }

    /**
     * Prepares for a retry attempt, logging and adding delay if needed.
     *
     * @param attempt The current attempt number
     */
    private void prepareForAttempt(int attempt) {
        if (attempt > 0) {
            log.info("Retry attempt {} for NER service", attempt);
            performRetryDelay();
        }
    }

    /**
     * Selects the appropriate service URL based on the current attempt.
     *
     * @param attempt The current attempt number
     * @return The service URL to use
     */
    private String selectServiceUrl(int attempt) {
        return attempt > 0 && !backupNerServiceUrl.isEmpty() ? backupNerServiceUrl : nerServiceUrl;
    }

    /**
     * Handles a failed request attempt.
     *
     * @param attempt The current attempt number
     * @return true if we should use fallback detection
     */
    private boolean handleFailedAttempt(int attempt) {
        recordFailure();
        if (attempt >= maxRetries) {
            log.warn("Max retries exceeded. Using fallback patterns.");
            return true;
        }
        return false;
    }

    /**
     * Handles a ResourceAccessException.
     *
     * @param e       The exception that occurred
     * @param attempt The current attempt number
     * @return true if we should use fallback detection
     */
    private boolean handleResourceException(ResourceAccessException e, int attempt) {
        handleBatchRequestException(e, "Resource access exception calling NER service");
        return attempt >= maxRetries || circuitOpen.get();
    }

    /**
     * Handles a general exception.
     *
     * @param e       The exception that occurred
     * @param attempt The current attempt number
     * @return true if we should use fallback detection
     */
    private boolean handleGeneralException(Exception e, int attempt) {
        handleBatchRequestException(e, "Exception when calling NER service");
        return attempt >= maxRetries;
    }

    /**
     * Handles exceptions during batch processing.
     *
     * @param e            The exception that occurred
     * @param errorMessage Error message to log
     */
    private void handleBatchRequestException(Exception e, String errorMessage) {
        log.error("{}: {}", errorMessage, e.getMessage(), e);
        recordFailure();
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
     *
     * @param url     The service URL to use
     * @param samples List of text samples to process
     * @return Map of results (empty map if the request failed)
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
            resetCircuitBreaker();
            return response.getBody();
        }

        log.warn("NER service returned unsuccessful status code: {}", response.getStatusCode());
        return Collections.emptyMap();
    }

    /**
     * Processes results from a batch request.
     *
     * @param columnDataMap Original map of column data
     * @param batchData     Batch processing data
     * @param batchResults  Results from the NER service
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
                        String cacheKey = columnName + "_" + String.join("_",
                                columnDataMap.get(columnName));
                        nerResultsCache.put(cacheKey, columnResults);
                    } else {
                        log.error("Index out of bounds when processing batch results for column: {}", columnName);
                        batchData.results.put(columnName, Collections.emptyMap());
                    }
                }
            }
        }
    }

    /**
     * Fallback method that applies regex patterns when NER service is unavailable.
     * Uses predefined patterns to detect common PII types.
     * 
     * @param columnDataMap Map of column names to text samples
     * @return Map of column names to detected entity types with confidence levels
     */
    private Map<String, Map<String, Double>> applyFallbackDetection(Map<String, List<String>> columnDataMap) {
        Map<String, Map<String, Double>> results = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : columnDataMap.entrySet()) {
            String columnName = entry.getKey();
            List<String> samples = entry.getValue();

            if (samples == null || samples.isEmpty()) {
                results.put(columnName, Collections.emptyMap());
                continue;
            }

            Map<String, Double> columnResults = processColumnSamples(samples);
            results.put(columnName, columnResults);

            // Cache the results
            cacheResults(columnName, samples, columnResults);
        }

        return results;
    }

    /**
     * Processes samples for a single column to detect PII types.
     * 
     * @param samples List of text samples to analyze
     * @return Map of entity types to confidence levels
     */
    private Map<String, Double> processColumnSamples(List<String> samples) {
        Map<String, Integer> matchCounts = countPatternMatches(samples);
        return calculateConfidenceScores(matchCounts, samples.size());
    }

    /**
     * Counts pattern matches across all samples.
     * 
     * @param samples List of text samples to analyze
     * @return Map of entity types to match counts
     */
    private Map<String, Integer> countPatternMatches(List<String> samples) {
        Map<String, Integer> matchCounts = new HashMap<>();

        for (String sample : samples) {
            if (sample == null || sample.isEmpty()) {
                continue;
            }

            applyPatternsToSample(sample, matchCounts);
        }

        return matchCounts;
    }

    /**
     * Applies all patterns to a single sample and updates match counts.
     * 
     * @param sample      The text sample to check
     * @param matchCounts Map to update with match counts
     */
    private void applyPatternsToSample(String sample, Map<String, Integer> matchCounts) {
        for (Map.Entry<String, Pattern> patternEntry : FALLBACK_PATTERNS.entrySet()) {
            String piiType = patternEntry.getKey();
            Pattern pattern = patternEntry.getValue();

            Matcher matcher = pattern.matcher(sample);
            if (matcher.find()) {
                matchCounts.put(piiType, matchCounts.getOrDefault(piiType, 0) + 1);
            }
        }
    }

    /**
     * Calculates confidence scores based on match counts.
     * 
     * @param matchCounts Map of entity types to match counts
     * @param sampleSize  Total number of samples
     * @return Map of entity types to confidence levels
     */
    private Map<String, Double> calculateConfidenceScores(Map<String, Integer> matchCounts, int sampleSize) {
        Map<String, Double> columnResults = new HashMap<>();

        for (Map.Entry<String, Integer> countEntry : matchCounts.entrySet()) {
            String piiType = countEntry.getKey();
            int count = countEntry.getValue();
            double confidence = (double) count / sampleSize * 0.85; // Cap at 85% confidence for regex fallback

            if (confidence > 0.25) { // Only include types with reasonable confidence
                columnResults.put(piiType, confidence);
            }
        }

        return columnResults;
    }

    /**
     * Caches results for a column.
     * 
     * @param columnName Name of the column
     * @param samples    List of samples for the column
     * @param results    Detection results
     */
    private void cacheResults(String columnName, List<String> samples, Map<String, Double> results) {
        String cacheKey = columnName + "_" + String.join("_", samples);
        nerResultsCache.put(cacheKey, results);
    }

    /**
     * Aggregates individual sample results for a column into a single confidence
     * map.
     *
     * @param sampleResults List of entity detection results for each sample
     * @return Map of entity types to confidence levels
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
     * Analyzes text with the NER service.
     *
     * @param textSamples List of text samples to analyze
     * @return Map of detected entity types with their confidence level
     */
    public Map<String, Double> analyzeText(List<String> textSamples) {
        if (textSamples == null || textSamples.isEmpty()) {
            log.warn("No text to analyze");
            return Collections.emptyMap();
        }

        // Check if circuit breaker is open
        if (isCircuitOpen()) {
            log.warn("Circuit breaker is open. Using fallback patterns for single sample NER detection.");
            return useFallbackForSingleAnalysis(textSamples);
        }

        // Check cache first
        String cacheKey = String.join("_", textSamples);
        if (nerResultsCache.containsKey(cacheKey)) {
            return nerResultsCache.get(cacheKey);
        }

        return processSingleAnalysisWithRetries(textSamples, cacheKey);
    }

    /**
     * Processes a single analysis request with retry logic.
     * 
     * @param textSamples The text samples to analyze
     * @param cacheKey    The cache key for these samples
     * @return Analysis results
     */
    private Map<String, Double> processSingleAnalysisWithRetries(List<String> textSamples, String cacheKey) {
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
                    nerResultsCache.put(cacheKey, result);
                    return result;
                } else {
                    recordFailure();
                    if (attempt >= maxRetries) {
                        return useFallbackForSingleAnalysis(textSamples);
                    }
                }
            } catch (Exception e) {
                handleSingleAnalysisException(e);
                if (attempt >= maxRetries || circuitOpen.get()) {
                    return useFallbackForSingleAnalysis(textSamples);
                }
            }
        }

        // If we reach here, all attempts failed
        return useFallbackForSingleAnalysis(textSamples);
    }

    /**
     * Handles exceptions during single analysis requests.
     * 
     * @param e The exception that occurred
     */
    private void handleSingleAnalysisException(Exception e) {
        if (e instanceof ResourceAccessException) {
            log.error("Resource access exception calling NER service: {}", e.getMessage());
        } else {
            log.error("Exception when calling NER service: {}", e.getMessage(), e);
        }
        recordFailure();
    }

    /**
     * Sends a single analysis request to the NER service.
     * 
     * @param url         The service URL
     * @param textSamples The text samples to analyze
     * @return Analysis results or an empty map if the request failed
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
            resetCircuitBreaker();
            return response.getBody();
        }

        return Collections.emptyMap();
    }

    /**
     * Uses fallback detection for a single analysis.
     * 
     * @param textSamples The text samples to analyze
     * @return Fallback analysis results
     */
    private Map<String, Double> useFallbackForSingleAnalysis(List<String> textSamples) {
        return applyFallbackDetection(Collections.singletonMap("sample", textSamples))
                .getOrDefault("sample", Collections.emptyMap());
    }

    /**
     * Creates a standard RestTemplate with reasonable timeouts.
     *
     * @return Configured RestTemplate
     */
    private RestTemplate createStandardRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        // Set reasonable timeouts
        requestFactory.setConnectTimeout(5000); // 5 seconds
        requestFactory.setReadTimeout(30000); // 30 seconds

        return new RestTemplate(requestFactory);
    }

    /**
     * Checks if the NER service is available.
     * Takes circuit breaker state into account.
     *
     * @return true if the service is available
     */
    public boolean isServiceAvailable() {
        // If circuit is open, try to see if it can be reset
        if (circuitOpen.get() && System.currentTimeMillis() > circuitResetTime.get()) {
            log.info("Circuit breaker timeout elapsed, attempting reset...");
            circuitOpen.set(false);
        }

        // If circuit is still open, return false immediately
        if (circuitOpen.get()) {
            log.debug("Circuit breaker is open, reporting service as unavailable");
            return false;
        }

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    nerServiceUrl.replace("/ner", "/health"),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                resetCircuitBreaker();
                return true;
            } else {
                recordFailure();
                return false;
            }
        } catch (Exception e) {
            log.warn("NER service not available: {}", e.getMessage());
            recordFailure();

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

    /**
     * Records a service failure and potentially opens the circuit breaker if
     * failure threshold is reached.
     */
    private synchronized void recordFailure() {
        consecutiveFailures++;
        log.debug("NER service failure recorded: {} consecutive failures", consecutiveFailures);

        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            log.warn("Circuit breaker threshold reached ({}), opening circuit", FAILURE_THRESHOLD);
            circuitOpen.set(true);
            circuitResetTime.set(System.currentTimeMillis() + CIRCUIT_RESET_TIMEOUT_MS);
        }
    }

    /**
     * Resets the circuit breaker on successful operation.
     */
    private synchronized void resetCircuitBreaker() {
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
     * @return true if circuit is open
     */
    private boolean isCircuitOpen() {
        // If circuit breaker timeout has elapsed, allow a test request
        if (circuitOpen.get() && System.currentTimeMillis() > circuitResetTime.get()) {
            log.info("Circuit breaker in half-open state, allowing test request");
            return false;
        }
        return circuitOpen.get();
    }

    /**
     * Clears the NER results cache.
     */
    public void clearCache() {
        nerResultsCache.clear();
        log.info("NER results cache cleared");
    }

    /**
     * Manually resets the circuit breaker for testing purposes.
     */
    public void resetCircuitBreakerForTesting() {
        consecutiveFailures = 0;
        circuitOpen.set(false);
        log.info("Circuit breaker manually reset");
    }
}