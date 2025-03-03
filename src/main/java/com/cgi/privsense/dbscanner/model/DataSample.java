package com.cgi.privsense.dbscanner.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@ToString
public class DataSample implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String tableName;
    private final List<String> columnNames;
    private final List<Map<String, Object>> rows;
    private final int totalRows;
    private final int sampleSize;

    public static DataSample fromRows(String tableName, List<Map<String, Object>> rows) {
        return DataSample.builder()
                .tableName(tableName)
                .rows(rows)
                .columnNames(rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet()))
                .totalRows(rows.size())
                .sampleSize(rows.size())
                .build();
    }

    public DataSample(String tableName, List<Map<String, Object>> rows) {
        this.tableName = tableName;
        this.rows = rows;
        this.columnNames = rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet());
        this.totalRows = rows.size();
        this.sampleSize = rows.size();
    }

    public DataSample(String tableName, List<String> columnNames, List<Map<String, Object>> rows, int totalRows, int sampleSize) {
        this.tableName = tableName;
        this.columnNames = columnNames;
        this.rows = rows;
        this.totalRows = totalRows;
        this.sampleSize = sampleSize;
    }
}
