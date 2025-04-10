package com.cgi.privsense.common.constants;

/**
 * Constants for database types and operations.
 */
public final class DatabaseConstants {
    
    /**
     * Database type constants
     */
    public static final String DB_TYPE_MYSQL = "mysql";
    public static final String DB_TYPE_POSTGRESQL = "postgresql";
    public static final String DB_TYPE_ORACLE = "oracle";
    public static final String DB_TYPE_SQLSERVER = "sqlserver";
    public static final String DB_TYPE_DEFAULT= "default";
    
    /**
     * SQL validation query constants
     */
    public static final String VALIDATION_QUERY_SIMPLE = "SELECT 1";
    public static final String VALIDATION_QUERY_ORACLE = "SELECT 1 FROM DUAL";
    
    /**
     * Private constructor to prevent instantiation.
     */
    private DatabaseConstants() {
        // Utility class, no instantiation
    }
}