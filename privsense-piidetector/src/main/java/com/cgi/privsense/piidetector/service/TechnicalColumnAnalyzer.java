package com.cgi.privsense.piidetector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service responsible for determining if a column is technical
 * and should be skipped during PII analysis.
 */
@Component
public class TechnicalColumnAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(TechnicalColumnAnalyzer.class);

    // Common technical columns that can be safely skipped
    private static final Set<String> SKIP_COLUMNS = new HashSet<>(Arrays.asList(
            "id", "created_at", "updated_at", "created_by", "modified_at", "rowid", "uuid"
    ));

    // Pattern to identify technical columns - simplified to reduce complexity
    private static final Pattern TECHNICAL_COLUMN_PATTERN = Pattern.compile(
            "^(.*_id|id|pk|fk|seq|sequence|timestamp|version|active|(is|has)_.*|flag)$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Checks if a column should be skipped based on naming patterns.
     *
     * @param columnName Column name to check
     * @return true if column should be skipped
     */
    public boolean shouldSkipColumn(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return false;
        }

        String normalizedName = columnName.toLowerCase();

        // Check against known technical column names
        if (SKIP_COLUMNS.contains(normalizedName)) {
            return true;
        }

        // Check against technical column pattern
        return TECHNICAL_COLUMN_PATTERN.matcher(normalizedName).matches();
    }

    /**
     * Adds custom technical column names to the skip list.
     * 
     * @param columnNames Column names to add
     */
    public void addCustomTechnicalColumns(String... columnNames) {
        if (columnNames != null && columnNames.length > 0) {
            for (String name : columnNames) {
                if (name != null && !name.isEmpty()) {
                    SKIP_COLUMNS.add(name.toLowerCase());
                }
            }
            log.info("Added {} custom technical column names to skip list", columnNames.length);
        }
    }
}