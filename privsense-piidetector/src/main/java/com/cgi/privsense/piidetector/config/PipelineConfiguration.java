package com.cgi.privsense.piidetector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for PII detection pipeline.
 * Allows for externalized configuration of the pipeline behavior
 * through application.yml or environment variables.
 */
@Configuration
@ConfigurationProperties(prefix = "piidetector.pipeline")
public class PipelineConfiguration {
    
    /**
     * Ordered list of detection stages to execute.
     * Default: ["heuristic", "regex", "ner"]
     */
    private List<String> stages = new ArrayList<>(List.of("heuristic", "regex", "ner"));
    
    /**
     * Confidence threshold for early termination of pipeline.
     * Default: 0.7
     */
    private double confidenceThreshold = 0.7;
    
    /**
     * Minimum sample size for detection.
     * Default: 5
     */
    private int minSampleSize = 5;
    
    /**
     * Maximum sample size for detection.
     * Default: 20
     */
    private int maxSampleSize = 20;
    
    /**
     * Strategy-specific configuration options.
     */
    private Map<String, StrategyConfig> strategies = new HashMap<>();
    
    /**
     * Feature flags for enabling/disabling specific features.
     */
    private Map<String, Boolean> features = new HashMap<>();
    
    /**
     * Default constructor initializes default strategy configs.
     */
    public PipelineConfiguration() {
        // Initialize with default strategy configs
        strategies.put("heuristic", new StrategyConfig(true, 0.6));
        strategies.put("regex", new StrategyConfig(true, 0.7));
        strategies.put("ner", new StrategyConfig(true, 0.8));
        
        // Default feature flags
        features.put("caching", true);
        features.put("adaptiveSampling", true);
        features.put("earlyTermination", true);
        features.put("contextEnhancement", true);
    }
    
    // Configuration class for individual strategies
    public static class StrategyConfig {
        private boolean enabled;
        private double confidenceThreshold;
        
        public StrategyConfig() {
            this(true, 0.7);
        }
        
        public StrategyConfig(boolean enabled, double confidenceThreshold) {
            this.enabled = enabled;
            this.confidenceThreshold = confidenceThreshold;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }
        
        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }
    }

    public List<String> getStages() {
        return stages;
    }

    public void setStages(List<String> stages) {
        this.stages = stages;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public int getMinSampleSize() {
        return minSampleSize;
    }

    public void setMinSampleSize(int minSampleSize) {
        this.minSampleSize = minSampleSize;
    }

    public int getMaxSampleSize() {
        return maxSampleSize;
    }

    public void setMaxSampleSize(int maxSampleSize) {
        this.maxSampleSize = maxSampleSize;
    }

    public Map<String, StrategyConfig> getStrategies() {
        return strategies;
    }

    public void setStrategies(Map<String, StrategyConfig> strategies) {
        this.strategies = strategies;
    }

    public Map<String, Boolean> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Boolean> features) {
        this.features = features;
    }
    
    /**
     * Checks if a feature is enabled.
     * 
     * @param featureName Feature name
     * @return True if the feature is enabled
     */
    public boolean isFeatureEnabled(String featureName) {
        return features.getOrDefault(featureName, false);
    }
    
    /**
     * Gets the configuration for a specific strategy.
     * 
     * @param strategyName Strategy name
     * @return Strategy configuration
     */
    public StrategyConfig getStrategyConfig(String strategyName) {
        return strategies.getOrDefault(strategyName, new StrategyConfig());
    }
}