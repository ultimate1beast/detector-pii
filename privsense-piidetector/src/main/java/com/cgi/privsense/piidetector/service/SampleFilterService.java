package com.cgi.privsense.piidetector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service responsible for filtering and preparing data samples for PII detection.
 */
@Component
public class SampleFilterService {
    private static final Logger log = LoggerFactory.getLogger(SampleFilterService.class);

    /**
     * Pre-filters samples to optimize processing.
     * Removes null values and applies quick validation checks.
     *
     * @param samples Original samples
     * @return Filtered samples
     */
    public List<Object> preFilterSamples(List<Object> samples) {
        if (samples == null || samples.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> filteredSamples = new ArrayList<>(samples.size());
        for (Object sample : samples) {
            if (sample != null && !sample.toString().isEmpty()) {
                filteredSamples.add(sample);
            }
        }

        log.debug("Pre-filtered samples: {} original, {} after filtering", 
            samples.size(), filteredSamples.size());
        
        return filteredSamples;
    }
    
    /**
     * Applies statistical sampling to input data.
     * For scenarios where we want to limit the amount of data processed.
     * 
     * @param samples Original samples
     * @param maxSamples Maximum number of samples to return
     * @return Statistical sample of the data
     */
    public List<Object> statisticalSampling(List<Object> samples, int maxSamples) {
        if (samples == null || samples.isEmpty() || maxSamples <= 0 || samples.size() <= maxSamples) {
            return samples;
        }
        
        List<Object> result = new ArrayList<>(maxSamples);
        
        // Calculate step size for evenly distributed sampling
        double step = (double) samples.size() / maxSamples;
        
        for (int i = 0; i < maxSamples; i++) {
            int index = Math.min((int)(i * step), samples.size() - 1);
            result.add(samples.get(index));
        }
        
        log.debug("Statistical sampling: {} original, {} samples", samples.size(), result.size());
        
        return result;
    }
}