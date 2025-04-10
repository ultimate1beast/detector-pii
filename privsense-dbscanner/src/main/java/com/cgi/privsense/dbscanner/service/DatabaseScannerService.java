package com.cgi.privsense.dbscanner.service;

import com.cgi.privsense.dbscanner.core.datasource.DataSourceProvider;
import com.cgi.privsense.dbscanner.core.scanner.DatabaseScanner;
import com.cgi.privsense.dbscanner.core.scanner.DatabaseScannerFactory;
import com.cgi.privsense.dbscanner.exception.DatabaseOperationException;
import com.cgi.privsense.dbscanner.model.ColumnMetadata;
import com.cgi.privsense.dbscanner.model.DataSample;
import com.cgi.privsense.dbscanner.model.RelationshipMetadata;
import com.cgi.privsense.dbscanner.model.TableMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.function.Function;

/**
 * Service for scanning databases with template methods.
 * Optimized implementation with better error handling and reduced duplication.
 */
@Service
public class DatabaseScannerService implements ScannerService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseScannerService.class);

    /**
     * Factory for creating database scanners.
     */
    private final DatabaseScannerFactory scannerFactory;

    /**
     * Provider for data sources.
     */
    private final DataSourceProvider dataSourceProvider;

    /**
     * Constructor.
     *
     * @param scannerFactory Factory for creating database scanners
     * @param dataSourceProvider Provider for data sources
     */
    public DatabaseScannerService(
            DatabaseScannerFactory scannerFactory,
            DataSourceProvider dataSourceProvider) {
        this.scannerFactory = scannerFactory;
        this.dataSourceProvider = dataSourceProvider;
    }

    /**
     * Gets a scanner for the specified database type and data source.
     *
     * @param dbType Database type
     * @param dataSourceName Data source name
     * @return Database scanner
     */
    private DatabaseScanner getScanner(String dbType, String dataSourceName) {
        logger.debug("Getting scanner for db type: {} and datasource: {}", dbType, dataSourceName);
        DataSource dataSource = dataSourceProvider.getDataSource(dataSourceName);
        return scannerFactory.getScanner(dbType, dataSource);
    }

    /**
     * Template method for scanner operations to reduce code duplication.
     *
     * @param operation Name of the operation for logging
     * @param dbType Database type
     * @param dataSourceName Data source name
     * @param scannerFunction Function to execute with the scanner
     * @param <T> Return type
     * @return Result of the scanner function
     */
    private <T> T executeScannerOperation(String operation, String dbType, String dataSourceName,
                                          Function<DatabaseScanner, T> scannerFunction) {
        try {
            logger.info("Executing {} for: {}/{}", operation, dbType, dataSourceName);
            DatabaseScanner scanner = getScanner(dbType, dataSourceName);
            T result = scannerFunction.apply(scanner);
            logger.debug("{} completed successfully", operation);
            return result;
        } catch (Exception e) {
           
            throw DatabaseOperationException.scannerError("Failed to execute " + operation, e);
        }
    }

    /**
     * Scans all tables in a database.
     *
     * @param dbType Database type
     * @param dataSourceName Data source name
     * @return List of table metadata
     */
    @Override
    @Cacheable(value = "tableMetadata", key = "#dbType + '-' + #dataSourceName")
    public List<TableMetadata> scanTables(String dbType, String dataSourceName) {
        return executeScannerOperation("scanTables", dbType, dataSourceName, DatabaseScanner::scanTables);
    }

    /**
     * Scans a specific table.
     *
     * @param dbType Database type
     * @param dataSourceName Data source name
     * @param tableName Table name
     * @return Table metadata
     */
    @Override
    @Cacheable(value = "tableMetadata", key = "#dbType + '-' + #dataSourceName + '-' + #tableName")
    public TableMetadata scanTable(String dbType, String dataSourceName, String tableName) {
        return executeScannerOperation("scanTable", dbType, dataSourceName, scanner -> scanner.scanTable(tableName));
    }

    /**
     * Scans the columns of a table.
     *
     * @param dbType Database type
     * @param dataSourceName Data source name
     * @param tableName Table name
     * @return List of column metadata
     */
    @Override
    @Cacheable(value = "columnMetadata", key = "#dbType + '-' + #dataSourceName + '-' + #tableName")
    public List<ColumnMetadata> scanColumns(String dbType, String dataSourceName, String tableName) {
        return executeScannerOperation("scanColumns", dbType, dataSourceName, scanner -> scanner.scanColumns(tableName));
    }

    /**
     * Samples data from a table.
     *
     * @param dbType Database type
     * @param dataSourceName Data source name
     * @param tableName Table name
     * @param limit Maximum number of rows
     * @return Data sample
     */
    @Override
    public DataSample sampleData(String dbType, String dataSourceName, String tableName, int limit) {
        return executeScannerOperation("sampleData", dbType, dataSourceName,
                scanner -> scanner.sampleTableData(tableName, limit));
    }

    /**
     * Samples data from a column.
     *
     * @param dbType Database type
     * @param dataSourceName Data source name
     * @param tableName Table name
     * @param columnName Column name
     * @param limit Maximum number of values
     * @return List of sampled values
     */
    @Override
    public List<Object> sampleColumnData(String dbType, String dataSourceName, String tableName, String columnName, int limit) {
        return executeScannerOperation("sampleColumnData", dbType, dataSourceName,
                scanner -> scanner.sampleColumnData(tableName, columnName, limit));
    }

    /**
     * Gets detailed relationships for a table.
     *
     * @param dbType Database type
     * @param dataSourceName Data source name
     * @param tableName Table name
     * @return List of relationship metadata
     */
    @Override
    @Cacheable(value = "relationshipMetadata", key = "#dbType + '-' + #dataSourceName + '-' + #tableName")
    public List<RelationshipMetadata> getTableRelationships(String dbType, String dataSourceName, String tableName) {
        return executeScannerOperation("getTableRelationships", dbType, dataSourceName,
                scanner -> scanner.getTableRelationships(tableName));
    }
}