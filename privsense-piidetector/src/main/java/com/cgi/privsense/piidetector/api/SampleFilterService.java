package com.cgi.privsense.piidetector.api;

import java.util.List;
import java.util.Map;

/**
 * Interface for filtering and preparing data samples for PII detection.
 * Handles sample selection, cleaning, and normalization to optimize detection effectiveness.
 */
public interface SampleFilterService {
    
    /**
     * Filters a list of text samples to find the most representative ones.
     * Removes duplicates, empty values, and selects a balanced set of data.
     * 
     * @param samples List of raw data samples
     * @param maxSampleSize Maximum number of samples to return
     * @return Filtered list of samples
     */
    List<String> filterSamples(List<String> samples, int maxSampleSize);
    
    /**
     * Processes raw column data from multiple columns.
     * 
     * @param columnData Map of column names to lists of raw data samples
     * @param maxSamplesPerColumn Maximum number of samples to process per column
     * @return Map of column names to filtered sample lists
     */
    Map<String, List<String>> prepareBatchData(Map<String, List<String>> columnData, int maxSamplesPerColumn);
    
    /**
     * Cleans a single text sample by removing unnecessary characters and normalizing format.
     * 
     * @param sample Raw text sample
     * @return Cleaned text sample
     */
    String cleanSample(String sample);
    
    /**
     * Determines if a sample is likely to contain meaningful data for PII detection.
     * 
     * @param sample Text sample to evaluate
     * @return true if the sample may contain meaningful data
     */
    boolean isMeaningfulSample(String sample);
}