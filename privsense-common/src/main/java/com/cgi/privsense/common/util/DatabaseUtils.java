package com.cgi.privsense.common.util;

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
            case "mysql":
                return "`" + identifier.replace("`", "``") + "`";
            case "postgresql", "oracle":
                return "\"" + identifier.replace("\"", "\"\"") + "\"";
            case "sqlserver":
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
        String dbTypeLower = (dbType != null) ? dbType.toLowerCase() : "mysql";

        return switch (dbTypeLower) {
            case "mysql" -> String.format(
                    "jdbc:mysql://%s:%d/%s",
                    host,
                    port != null ? port : 3306,
                    database
            );
            case "postgresql" -> String.format(
                    "jdbc:postgresql://%s:%d/%s",
                    host,
                    port != null ? port : 5432,
                    database
            );
            case "oracle" -> String.format(
                    "jdbc:oracle:thin:@%s:%d/%s",
                    host,
                    port != null ? port : 1521,
                    database
            );
            case "sqlserver" -> String.format(
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
        String type = (dbType != null) ? dbType.toLowerCase() : "mysql";
        return switch (type) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql" -> "org.postgresql.Driver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            case "sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        };
    }
}