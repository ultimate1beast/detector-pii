package com.cgi.privsense.common.util;

import com.cgi.privsense.common.constants.DatabaseConstants;
import lombok.experimental.UtilityClass;

/**
 * Utility class for database operations.
 */
@UtilityClass
public class DatabaseUtils {

    /**
     * Escapes an identifier according to the database type.
     *
     * @param identifier Identifier to escape
     * @param dbType Database type
     * @return Escaped identifier
     */
    public String escapeIdentifier(String identifier, String dbType) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("Identifier cannot be null or empty");
        }

        switch (dbType.toLowerCase()) {
            case DatabaseConstants.DB_TYPE_MYSQL:
                return "`" + identifier.replace("`", "``") + "`";
            case DatabaseConstants.DB_TYPE_POSTGRESQL, DatabaseConstants.DB_TYPE_ORACLE:
                return "\"" + identifier.replace("\"", "\"\"") + "\"";
            case DatabaseConstants.DB_TYPE_SQLSERVER:
                return "[" + identifier.replace("]", "]]") + "]";
            default:
                return identifier;
        }
    }

    /**
     * Validates a table name.
     *
     * @param tableName Table name to validate
     * @throws IllegalArgumentException If the name is invalid
     */
    public void validateTableName(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }

        // Additional validation if needed
        if (tableName.contains(";") || tableName.contains("--")) {
            throw new IllegalArgumentException("Table name contains invalid characters");
        }
    }

    /**
     * Validates a column name.
     *
     * @param columnName Column name to validate
     * @throws IllegalArgumentException If the name is invalid
     */
    public void validateColumnName(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) {
            throw new IllegalArgumentException("Column name cannot be null or empty");
        }

        // Additional validation if needed
        if (columnName.contains(";") || columnName.contains("--")) {
            throw new IllegalArgumentException("Column name contains invalid characters");
        }
    }

    /**
     * Builds a JDBC URL based on the database type and connection parameters.
     *
     * @param dbType Database type (mysql, postgresql, oracle, etc.)
     * @param host Database host
     * @param port Database port (can be null for default)
     * @param database Database name
     * @return JDBC URL
     */
    public String buildJdbcUrl(String dbType, String host, Integer port, String database) {
        String dbTypeLower = (dbType != null) ? dbType.toLowerCase() : DatabaseConstants.DB_TYPE_MYSQL;

        return switch (dbTypeLower) {
            case DatabaseConstants.DB_TYPE_MYSQL -> String.format(
                    "jdbc:mysql://%s:%d/%s",
                    host,
                    port != null ? port : 3306,
                    database
            );
            case DatabaseConstants.DB_TYPE_POSTGRESQL -> String.format(
                    "jdbc:postgresql://%s:%d/%s",
                    host,
                    port != null ? port : 5432,
                    database
            );
            case DatabaseConstants.DB_TYPE_ORACLE -> String.format(
                    "jdbc:oracle:thin:@%s:%d/%s",
                    host,
                    port != null ? port : 1521,
                    database
            );
            case DatabaseConstants.DB_TYPE_SQLSERVER -> String.format(
                    "jdbc:sqlserver://%s:%d;databaseName=%s",
                    host,
                    port != null ? port : 1433,
                    database
            );
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        };
    }

    /**
     * Gets the default driver class name for a database type.
     *
     * @param dbType Database type
     * @return Driver class name
     */
    public String getDriverClassNameForDbType(String dbType) {
        String type = (dbType != null) ? dbType.toLowerCase() : DatabaseConstants.DB_TYPE_MYSQL;
        return switch (type) {
            case DatabaseConstants.DB_TYPE_MYSQL -> "com.mysql.cj.jdbc.Driver";
            case DatabaseConstants.DB_TYPE_POSTGRESQL -> "org.postgresql.Driver";
            case DatabaseConstants.DB_TYPE_ORACLE -> "oracle.jdbc.OracleDriver";
            case DatabaseConstants.DB_TYPE_SQLSERVER -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        };
    }
}