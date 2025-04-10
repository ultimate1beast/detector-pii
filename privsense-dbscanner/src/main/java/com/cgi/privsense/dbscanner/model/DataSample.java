package com.cgi.privsense.dbscanner.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a sample of data from a table.
 * Simplified implementation with a single entry point for creation.
 */
@Getter
@Builder
@ToString
public class DataSample implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Name of the table.
     */
    private final String tableName;

    /**
     * List of column names.
     */
    private final List<String> columnNames;

    /**
     * List of rows, where each row is a map of column name to value.
     */
    private final List<Map<String, Object>> rows;

    /**
     * Custom serialization logic to ensure only serializable objects are included.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        // Additional custom serialization if needed
    }

    /**
     * Custom deserialization logic.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Additional custom deserialization if needed
    }

    /**
     * Total number of rows in the table.
     */
    private final int totalRows;

    /**
     * Number of rows in this sample.
     */
    private final int sampleSize;

    /**
     * Creates a data sample from a list of rows.
     * Single factory method to replace multiple constructors and factory methods.
     *
     * @param tableName        Table name
     * @param rows             List of rows
     * @param totalRowsInTable Optional total rows in the table (if known)
     * @return Data sample
     */
    public static DataSample create(String tableName, List<Map<String, Object>> rows, Integer totalRowsInTable) {
        Objects.requireNonNull(tableName, "Table name cannot be null");

        // Handle null or empty rows
        List<Map<String, Object>> safeRows = rows != null ? rows : List.of();

        // Extract column names from the first row, or empty list if no rows
        List<String> columnNames = safeRows.isEmpty()
                ? List.of()
                : new ArrayList<>(safeRows.getFirst().keySet());

        // Use provided total rows or default to sample size
        int actualTotal = totalRowsInTable != null ? totalRowsInTable : safeRows.size();

        return DataSample.builder()
                .tableName(tableName)
                .rows(safeRows)
                .columnNames(columnNames)
                .totalRows(actualTotal)
                .sampleSize(safeRows.size())
                .build();
    }

    /**
     * Overloaded method when total rows equals sample size.
     *
     * @param tableName Table name
     * @param rows      List of rows
     * @return Data sample
     */
    public static DataSample create(String tableName, List<Map<String, Object>> rows) {
        return create(tableName, rows, null);
    }

    /**
     * Gets a subset of this sample containing only the first N rows.
     *
     * @param maxRows Maximum number of rows
     * @return A new DataSample with at most maxRows rows
     */
    public DataSample limit(int maxRows) {
        if (maxRows >= rows.size()) {
            return this; // No need to create a new object
        }

        List<Map<String, Object>> limitedRows = rows.subList(0, maxRows);
        return DataSample.builder()
                .tableName(tableName)
                .rows(limitedRows)
                .columnNames(columnNames)
                .totalRows(totalRows)
                .sampleSize(limitedRows.size())
                .build();
    }

    /**
     * Gets the column values for a specific column.
     *
     * @param columnName Name of the column
     * @return List of values for the column
     */
    public List<Object> getColumnValues(String columnName) {
        if (!columnNames.contains(columnName)) {
            return List.of();
        }

        List<Object> values = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            values.add(row.get(columnName));
        }
        return values;
    }

    /**
     * Checks if this sample has any rows.
     *
     * @return true if the sample contains at least one row
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * Gets the number of columns in this sample.
     *
     * @return Number of columns
     */
    public int getColumnCount() {
        return columnNames.size();
    }
}