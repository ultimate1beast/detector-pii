package com.cgi.privsense.dbscanner.service.sampling.util;

import com.cgi.privsense.dbscanner.model.DataSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for extracting and processing data samples.
 */
public class SamplingDataExtractor {
    private static final Logger log = LoggerFactory.getLogger(SamplingDataExtractor.class);

    private SamplingDataExtractor() {
        // Utility class - prevent instantiation
    }

    /**
     * Extracts column data from a data sample.
     *
     * @param columnNames List of column names to extract
     * @param sample      Data sample containing the data
     * @return Map of column name to list of values
     */
    public static Map<String, List<Object>> extractColumnDataFromSample(List<String> columnNames, DataSample sample) {
        if (sample == null || sample.getRows() == null || sample.getRows().isEmpty()) {
            log.warn("No data in sample to extract columns from");
            return Collections.emptyMap();
        }
        
        // Convert column names to lowercase for case-insensitive comparison
        Set<String> requestedColsLower = columnNames.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Initialize column data map
        Map<String, List<Object>> columnData = initializeColumnDataMap(columnNames, sample.getRows().size());

        // Process each row and extract the requested column values
        for (Map<String, Object> row : sample.getRows()) {
            processRowForColumnExtraction(row, columnNames, requestedColsLower, columnData);
        }

        return columnData;
    }

    /**
     * Initializes the column data map with empty lists.
     *
     * @param columnNames  List of column names
     * @param expectedSize Expected size of the lists
     * @return Map with initialized empty lists
     */
    private static Map<String, List<Object>> initializeColumnDataMap(List<String> columnNames, int expectedSize) {
        Map<String, List<Object>> columnData = HashMap.newHashMap(columnNames.size());
        for (String column : columnNames) {
            columnData.put(column, new ArrayList<>(expectedSize));
        }
        return columnData;
    }

    /**
     * Processes a single row to extract column values.
     *
     * @param row                The row to process
     * @param columnNames        Original column names
     * @param requestedColsLower Lowercase column names for case-insensitive matching
     * @param columnData         Map to store the extracted values
     */
    private static void processRowForColumnExtraction(Map<String, Object> row, List<String> columnNames,
            Set<String> requestedColsLower, Map<String, List<Object>> columnData) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String colName = entry.getKey();

            // Check for direct match or case-insensitive match
            if (columnNames.contains(colName) || requestedColsLower.contains(colName.toLowerCase())) {
                addValueToMatchingColumn(colName, entry.getValue(), columnNames, columnData);
            }
        }
    }

    /**
     * Adds a value to the matching column in the column data map.
     *
     * @param colName     Column name from the row
     * @param value       Value to add
     * @param columnNames List of requested column names
     * @param columnData  Map to store the values
     */
    private static void addValueToMatchingColumn(String colName, Object value, List<String> columnNames,
            Map<String, List<Object>> columnData) {
        List<Object> colValues = columnData.get(colName);

        if (colValues == null) {
            // Try to find by case-insensitive match
            for (String requestedCol : columnNames) {
                if (requestedCol.equalsIgnoreCase(colName)) {
                    colValues = columnData.get(requestedCol);
                    break;
                }
            }
        }

        if (colValues != null) {
            colValues.add(value);
        }
    }
}