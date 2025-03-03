package com.cgi.privsense.dbscanner.service;

import com.cgi.privsense.dbscanner.config.DynamicDataSourceConfig;
import com.cgi.privsense.dbscanner.core.DatabaseScanner;
import com.cgi.privsense.dbscanner.factory.DatabaseScannerFactory;
import com.cgi.privsense.dbscanner.model.ColumnMetadata;
import com.cgi.privsense.dbscanner.model.DataSample;
import com.cgi.privsense.dbscanner.model.TableMetadata;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseScannerService {
    private final DatabaseScannerFactory scannerFactory;
    private final DynamicDataSourceConfig dataSourceConfig;

    public DatabaseScannerService(DatabaseScannerFactory scannerFactory,
                                  DynamicDataSourceConfig dataSourceConfig) {
        this.scannerFactory = scannerFactory;
        this.dataSourceConfig = dataSourceConfig;
    }

    private DatabaseScanner getScanner(String dbType, String dataSourceName) {
        DataSource dataSource = dataSourceConfig.getDataSource(dataSourceName);
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource not found: " + dataSourceName);
        }
        return scannerFactory.getScanner(dbType, dataSource);
    }

    public List<TableMetadata> scanTables(String dbType, String dataSourceName) {
        return getScanner(dbType, dataSourceName).scanTables();
    }

    public TableMetadata scanTable(String dbType, String dataSourceName, String tableName) {
        return getScanner(dbType, dataSourceName).scanTable(tableName);
    }

    public List<ColumnMetadata> scanColumns(String dbType, String dataSourceName, String tableName) {
        return getScanner(dbType, dataSourceName).scanColumns(tableName);
    }

    public DataSample sampleData(String dbType, String dataSourceName, String tableName, int limit) {
        return getScanner(dbType, dataSourceName).sampleTableData(tableName, limit);
    }

    public List<Object> sampleColumnData(String dbType, String dataSourceName, String tableName, String columnName, int limit) {
        return getScanner(dbType, dataSourceName).sampleColumnData(tableName, columnName, limit);
    }

    public Map<String, String> getTableRelations(String dbType, String dataSourceName, String tableName) {
        return getScanner(dbType, dataSourceName).getTableRelations(tableName);
    }
}
