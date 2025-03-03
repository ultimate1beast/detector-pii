package com.cgi.privsense.dbscanner.core;

import com.cgi.privsense.dbscanner.model.ColumnMetadata;
import com.cgi.privsense.dbscanner.model.DataSample;
import com.cgi.privsense.dbscanner.model.TableMetadata;

import java.util.List;
import java.util.Map;

public interface DatabaseScanner{
    List<TableMetadata> scanTables();
    TableMetadata scanTable(String tableName);
    List<ColumnMetadata> scanColumns(String tableName);
    DataSample sampleTableData(String tableName, int limit);
    List<Object> sampleColumnData(String tableName, String columnName, int limit);
    Map<String, String> getTableRelations(String tableName);
    String getDatabaseType();
}
