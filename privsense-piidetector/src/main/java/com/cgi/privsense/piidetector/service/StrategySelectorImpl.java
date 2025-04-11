package com.cgi.privsense.piidetector.service;

import com.cgi.privsense.piidetector.api.StrategySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Implementation of the StrategySelector interface.
 * Selects and configures PII detection strategies based on column characteristics.
 */
@Service
public class StrategySelectorImpl implements StrategySelector {
    private static final Logger log = LoggerFactory.getLogger(StrategySelectorImpl.class);

    // Active status of each strategy
    private final Map<String, Boolean> activeStrategies = new ConcurrentHashMap<>();
    
    // Patterns for technical columns that should use specific strategies
    private final Map<String, Pattern> columnPatterns = new HashMap<>();
    
    /**
     * Constructor initializes default patterns and active strategies.
     */
    public StrategySelectorImpl() {
        // Initialize column name patterns
        columnPatterns.put("email", Pattern.compile("(?i).*email.*|.*mail.*|.*e_?mail.*"));
        columnPatterns.put("phone", Pattern.compile("(?i).*phone.*|.*mobile.*|.*cell.*|.*tel.*"));
        columnPatterns.put("name", Pattern.compile("(?i).*name.*|.*first.*|.*last.*|.*full.*|.*user.*"));
        columnPatterns.put("address", Pattern.compile("(?i).*address.*|.*location.*|.*city.*|.*state.*|.*zip.*|.*postal.*"));
        columnPatterns.put("ssn", Pattern.compile("(?i).*ssn.*|.*social.*|.*security.*"));
        columnPatterns.put("creditcard", Pattern.compile("(?i).*card.*|.*credit.*|.*payment.*"));
        columnPatterns.put("date", Pattern.compile("(?i).*date.*|.*birth.*|.*dob.*"));
        
        // All strategies are active by default
        activeStrategies.put("heuristic", true);
        activeStrategies.put("regex", true);
        activeStrategies.put("ner", true);
    }

    @Override
    public List<String> selectStrategiesForColumn(String columnName, String columnType, boolean hasSamples) {
        List<String> selectedStrategies = new ArrayList<>();
        
        // Heuristic strategy is always used first as it's fast and uses column names
        if (isStrategyActive("heuristic")) {
            selectedStrategies.add("heuristic");
        }
        
        // Only use regex and NER if we have samples
        if (hasSamples) {
            // For text-based column types, apply regex
            if (isTextBasedColumn(columnType) && isStrategyActive("regex")) {
                selectedStrategies.add("regex");
            }
            
            // Only use NER for certain column types that might contain natural language
            if (shouldApplyNER(columnName, columnType) && isStrategyActive("ner")) {
                selectedStrategies.add("ner");
            }
        }
        
        log.debug("Selected strategies for column {}: {}", columnName, selectedStrategies);
        return selectedStrategies;
    }

    @Override
    public void configureActiveStrategies(Map<String, Boolean> strategyMap) {
        strategyMap.forEach((name, active) -> {
            if (activeStrategies.containsKey(name)) {
                activeStrategies.put(name, active);
                log.info("Strategy '{}' set to: {}", name, active);
            } else {
                log.warn("Unknown strategy: {}", name);
            }
        });
    }

    @Override
    public boolean isStrategyActive(String strategyName) {
        return activeStrategies.getOrDefault(strategyName, false);
    }

    @Override
    public Map<String, Boolean> getActiveStrategies() {
        return new HashMap<>(activeStrategies);
    }
    
    /**
     * Determines if a column type is text-based (so regex can be applied).
     * 
     * @param columnType Database column type
     * @return true if column is text-based
     */
    private boolean isTextBasedColumn(String columnType) {
        if (columnType == null) {
            return false;
        }
        
        String type = columnType.toLowerCase();
        return type.contains("char") || 
               type.contains("text") || 
               type.contains("string") ||
               type.contains("varchar") ||
               type.contains("clob");
    }
    
    /**
     * Checks if NER should be applied to this column.
     * 
     * @param columnName Column name
     * @param columnType Database column type
     * @return true if NER should be applied
     */
    private boolean shouldApplyNER(String columnName, String columnType) {
        // Apply NER to text columns that might contain sentences (comments, descriptions, etc.)
        String nameLower = columnName.toLowerCase();
        
        if (!isTextBasedColumn(columnType)) {
            return false;
        }
        
        return nameLower.contains("comment") ||
               nameLower.contains("description") ||
               nameLower.contains("note") ||
               nameLower.contains("detail") ||
               nameLower.contains("message") ||
               nameLower.contains("text") ||
               nameLower.contains("info");
    }
}