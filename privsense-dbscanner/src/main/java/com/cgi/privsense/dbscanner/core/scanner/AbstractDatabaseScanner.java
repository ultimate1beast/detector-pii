package com.cgi.privsense.dbscanner.core.scanner;

import com.cgi.privsense.common.util.DatabaseUtils;
import com.cgi.privsense.dbscanner.exception.DatabaseOperationException;
import com.cgi.privsense.dbscanner.model.DataSample;
import lombok.Builder;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

/**
 * Abstract base class for database scanners.
 * Provides common functionality and template methods for all database scanners.
 */
public abstract class AbstractDatabaseScanner implements DatabaseScanner {
    /**
     * The JDBC template used to execute SQL queries.
     */
    protected final JdbcTemplate jdbcTemplate;

    /**
     * The database type.
     */
    protected final String dbType;

    /**
     * Logger for this class.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Global JDBC settings
     */
    private final JdbcSettings jdbcSettings;

    /**
     * Constructor.
     *
     * @param dataSource The data source
     * @param dbType     The database type
     */
    protected AbstractDatabaseScanner(DataSource dataSource, String dbType) {
        this(dataSource, dbType, JdbcSettings.builder().build());
    }

    /**
     * Constructor with JDBC settings.
     *
     * @param dataSource The data source
     * @param dbType     The database type
     * @param settings   JDBC settings
     */
    protected AbstractDatabaseScanner(DataSource dataSource, String dbType, JdbcSettings settings) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.dbType = dbType;
        this.jdbcSettings = settings;
        configureJdbcTemplate();
    }

    /**
     * Configures the JDBC template using settings.
     */
    private void configureJdbcTemplate() {
        jdbcTemplate.setFetchSize(jdbcSettings.getFetchSize());
        jdbcTemplate.setMaxRows(jdbcSettings.getMaxRows());
        jdbcTemplate.setQueryTimeout(jdbcSettings.getQueryTimeoutSeconds());
    }

    /**
     * Gets the database type.
     *
     * @return The database type
     */
    @Override
    public String getDatabaseType() {
        return dbType;
    }

    /**
     * Executes a query with standardized error handling.
     *
     * @param operationName Operation name for logging
     * @param query         Function that executes the query
     * @return Query result
     * @throws DatabaseOperationException On error
     */
    protected <T> T executeQuery(String operationName, Function<JdbcTemplate, T> query) {
        try {
            logger.debug("Executing operation: {}", operationName);
            T result = query.apply(jdbcTemplate);
            logger.debug("Operation completed successfully: {}", operationName);
            return result;
        } catch (DataAccessException e) {
            // Add context and rethrow
            throw DatabaseOperationException.scannerError(
                    String.format("Error during %s operation on %s database: %s",
                            operationName, dbType, e.getMessage()),
                    e);
        } catch (Exception e) {
            // Add context and rethrow
            throw DatabaseOperationException.scannerError(
                    String.format("Unexpected error during %s operation on %s database: %s",
                            operationName, dbType, e.getMessage()),
                    e);
        }
    }

    /**
     * Template method for sampling table data.
     * Implements the common pattern for all database types.
     * Dialect-specific implementations will be handled by subclasses.
     *
     * @param tableName Table name
     * @param limit     Maximum number of rows
     * @return Data sample
     */
    @Override
    public DataSample sampleTableData(String tableName, int limit) {
        DatabaseUtils.validateTableName(tableName);

        return executeQuery("sampleTableData", jdbc -> {
            DataSource ds = jdbc.getDataSource();
            if (ds == null) {
                throw DatabaseOperationException.samplingError("DataSource is null, cannot sample table: " + tableName,
                        null);
            }

            try (Connection conn = ds.getConnection();
                    PreparedStatement stmt = prepareSampleTableStatement(conn, tableName, limit);
                    ResultSet rs = stmt.executeQuery()) {

                List<Map<String, Object>> rows = new ArrayList<>();
                int columnCount = rs.getMetaData().getColumnCount();

                // Pre-fetch column names for improved performance
                String[] columnNames = new String[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    columnNames[i] = rs.getMetaData().getColumnName(i + 1);
                }

                while (rs.next()) {
                    Map<String, Object> row = HashMap.newHashMap(columnCount);
                    for (int i = 0; i < columnCount; i++) {
                        row.put(columnNames[i], JdbcUtils.getResultSetValue(rs, i + 1));
                    }
                    rows.add(row);
                }

                return DataSample.create(tableName, rows);
            } catch (SQLException e) {
                // Add context and rethrow
                throw DatabaseOperationException.samplingError("Error sampling table: " + tableName, e);
            }
        });
    }

    /**
     * Template method for sampling column data.
     * Implements the common pattern for all database types.
     * Dialect-specific implementations will be handled by subclasses.
     *
     * @param tableName  Table name
     * @param columnName Column name
     * @param limit      Maximum number of values
     * @return List of sampled values
     */
    @Override
    public List<Object> sampleColumnData(String tableName, String columnName, int limit) {
        DatabaseUtils.validateTableName(tableName);
        DatabaseUtils.validateColumnName(columnName);

        return executeQuery("sampleColumnData", jdbc -> {
            DataSource ds = jdbc.getDataSource();
            if (ds == null) {
                throw DatabaseOperationException.samplingError("DataSource is null, cannot sample column: "
                        + tableName + "." + columnName, null);
            }

            try (Connection conn = ds.getConnection();
                    PreparedStatement stmt = prepareSampleColumnStatement(conn, tableName, columnName, limit);
                    ResultSet rs = stmt.executeQuery()) {

                List<Object> values = new ArrayList<>(limit);
                while (rs.next()) {
                    values.add(JdbcUtils.getResultSetValue(rs, 1));
                }
                return values;
            } catch (SQLException e) {
                // Add context and rethrow
                throw DatabaseOperationException.samplingError(
                        "Error sampling column: " + tableName + "." + columnName, e);
            }
        });
    }

    /**
     * Builds and returns SQL for sampling table data.
     * Subclasses must override this to provide database-specific SQL generation.
     *
     * @param tableName Table name
     * @return SQL string for sampling
     */
    protected abstract String buildSampleTableSql(String tableName);

    /**
     * Creates a prepared statement for sampling table data.
     * Delegates to buildSampleTableSql which is overridden by database-specific subclasses.
     * <p>
     * NOTE: The caller is responsible for closing the returned PreparedStatement.
     * This method intentionally does not close the statement as it needs to be
     * used by the caller.
     *
     * @param connection Database connection
     * @param tableName  Table name
     * @param limit      Maximum number of rows
     * @return PreparedStatement that must be closed by the caller
     * @throws SQLException On SQL error
     */
    protected PreparedStatement prepareSampleTableStatement(Connection connection, String tableName, int limit)
            throws SQLException {
        String sql = buildSampleTableSql(tableName);
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement(sql);
            stmt.setInt(1, limit);
            return stmt;
        } catch (SQLException e) {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                    // Log suppressed exception
                    logger.warn("Error closing statement after exception", ex);
                    e.addSuppressed(ex);
                }
            }
            throw e;
        }
    }

    /**
     * Builds SQL for sampling column data.
     * Subclasses must override this to provide database-specific SQL generation.
     *
     * @param tableName  Table name
     * @param columnName Column name
     * @return SQL string for sampling column data
     */
    protected abstract String buildSampleColumnSql(String tableName, String columnName);

    /**
     * Creates a prepared statement for sampling column data.
     * Delegates to buildSampleColumnSql which is overridden by database-specific subclasses.
     * <p>
     * NOTE: The caller is responsible for closing the returned PreparedStatement.
     * This method intentionally does not close the statement as it needs to be
     * used by the caller.
     *
     * @param connection Database connection
     * @param tableName  Table name
     * @param columnName Column name
     * @param limit      Maximum number of rows
     * @return PreparedStatement that must be closed by the caller
     * @throws SQLException On SQL error
     */
    protected PreparedStatement prepareSampleColumnStatement(Connection connection, String tableName,
            String columnName, int limit)
            throws SQLException {
        String sql = buildSampleColumnSql(tableName, columnName);
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement(sql);
            stmt.setInt(1, limit);
            return stmt;
        } catch (SQLException e) {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                    // Log suppressed exception
                    logger.warn("Error closing statement after exception", ex);
                    e.addSuppressed(ex);
                }
            }
            throw e;
        }
    }

    /**
     * Checks if a table exists.
     * Subclasses may override this to provide database-specific implementation.
     *
     * @param tableName Table name
     * @return true if the table exists, false otherwise
     */
    protected boolean tableExists(String tableName) {
        DatabaseUtils.validateTableName(tableName);
        try {
            String sql = buildTableExistsSql(tableName);
            jdbcTemplate.queryForObject(sql, Integer.class);
            return true;
        } catch (Exception e) {
            logger.debug("Table doesn't exist or is not accessible: {}, reason: {}",
                    tableName, e.getMessage());
            return false;
        }
    }

    /**
     * Builds SQL for checking if a table exists.
     * Subclasses should override this to provide database-specific SQL generation.
     *
     * @param tableName Table name
     * @return SQL string for checking table existence
     */
    protected String buildTableExistsSql(String tableName) {
        return String.format("SELECT 1 FROM %s WHERE 1 = 0", escapeIdentifier(tableName));
    }

    /**
     * Escapes an SQL identifier to prevent SQL injection.
     * Delegates to DatabaseUtils for consistency.
     *
     * @param identifier Identifier to escape
     * @return Escaped identifier
     */
    protected String escapeIdentifier(String identifier) {
        return DatabaseUtils.escapeIdentifier(identifier, dbType);
    }

    /**
     * Immutable JDBC settings class for configuring database connections.
     * Uses Lombok @Value for immutability and @Builder for the builder pattern.
     */
    @Value
    @Builder
    public static class JdbcSettings {
        @Builder.Default
        int fetchSize = 1000;
        
        @Builder.Default
        int maxRows = 10000;
        
        @Builder.Default
        int queryTimeoutSeconds = 30;
    }
}