package com.cgi.privsense.dbscanner.service.sampling.util;

import com.cgi.privsense.common.util.DatabaseUtils;
import com.cgi.privsense.dbscanner.core.scanner.DatabaseScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for validating database objects and operations.
 */
public class SamplingValidationUtils {
    private static final Logger log = LoggerFactory.getLogger(SamplingValidationUtils.class);

    private SamplingValidationUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that columns exist in the table.
     * Uses a single metadata query for efficiency.
     *
     * @param scanner      Database scanner to use
     * @param tableName    Table name
     * @param columnNames  List of column names to validate
     * @return List of existing column names
     */
    public static List<String> validateColumnsExistence(DatabaseScanner scanner, String tableName, List<String> columnNames) {
        DatabaseUtils.validateTableName(tableName);

        try {
            // Get all column metadata for the table in a single query
            final Set<String> tableColumnNames = scanner.scanColumns(tableName)
                    .stream()
                    .map(col -> col.getName().toLowerCase())
                    .collect(Collectors.toSet());

            // Filter requested columns to only include ones that exist
            return columnNames.stream()
                    .filter(Objects::nonNull)
                    .filter(col -> !col.trim().isEmpty())
                    .filter(col -> tableColumnNames.contains(col.toLowerCase()))
                    .toList();

        } catch (Exception e) {
            log.error("Error validating columns: {}", e.getMessage());
            // Fall back to using all requested columns but filter out nulls and empty strings
            return columnNames.stream()
                    .filter(Objects::nonNull)
                    .filter(col -> !col.trim().isEmpty())
                    .toList();
        }
    }

    /**
     * Validates table names.
     * 
     * @param tableNames List of table names to validate
     * @return List of validated table names
     */
    public static List<String> validateTableNames(List<String> tableNames) {
        return tableNames.stream()
            .filter(Objects::nonNull)
            .filter(tableName -> !tableName.trim().isEmpty())
            .filter(tableName -> {
                try {
                    DatabaseUtils.validateTableName(tableName);
                    return true;
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid table name '{}': {}", tableName, e.getMessage());
                    return false;
                }
            })
            .toList();
    }
}