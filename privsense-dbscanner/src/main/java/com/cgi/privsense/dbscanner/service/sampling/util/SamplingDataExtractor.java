package com.cgi.privsense.dbscanner.service.sampling.util;

import com.cgi.privsense.dbscanner.model.DataSample;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for extracting data from samples.
 * Provides methods to extract specific columns from data samples.
 */
@UtilityClass
public class SamplingDataExtractor {

    /**
     * Extracts column data from a data sample.
     *
     * @param columnNames Column names to extract
     * @param sample      Data sample to extract from
     * @return Map of column name to list of values
     */
    public Map<String, List<Object>> extractColumnDataFromSample(List<String> columnNames, DataSample sample) {
        if (sample == null || sample.getRows() == null || sample.getRows().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<Object>> result = new HashMap<>(columnNames.size());

        // Initialize lists for each column
        for (String columnName : columnNames) {
            if (sample.getColumnNames().contains(columnName)) {
                result.put(columnName, new ArrayList<>(sample.getRows().size()));
            }
        }

        // Extract values for each column
        for (Map<String, Object> row : sample.getRows()) {
            for (String columnName : columnNames) {
                if (result.containsKey(columnName)) {
                    result.get(columnName).add(row.get(columnName));
                }
            }
        }

        return result;
    }

    /**
     * Extracts data for a single column from a data sample.
     *
     * @param columnName Column name to extract
     * @param sample     Data sample to extract from
     * @return List of values for the column
     */
    public List<Object> extractColumnDataFromSample(String columnName, DataSample sample) {
        if (sample == null || sample.getRows() == null || sample.getRows().isEmpty()) {
            return Collections.emptyList();
        }

        if (!sample.getColumnNames().contains(columnName)) {
            return Collections.emptyList();
        }

        List<Object> result = new ArrayList<>(sample.getRows().size());

        for (Map<String, Object> row : sample.getRows()) {
            result.add(row.get(columnName));
        }

        return result;
    }
}