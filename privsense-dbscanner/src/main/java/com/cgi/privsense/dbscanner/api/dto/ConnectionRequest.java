package com.cgi.privsense.dbscanner.api.dto;

import com.cgi.privsense.dbscanner.config.dtoconfig.DatabaseConnectionRequest;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * DTO for connection requests from the API.
 * Simplified implementation with cleaner ID generation.
 */
@Data
public class ConnectionRequest {
    /**
     * Connection name - will be automatically generated if not provided.
     */
    private String name;

    /**
     * Database type.
     */
    private String dbType;

    /**
     * Database host.
     */
    private String host;

    /**
     * Database port.
     */
    private Integer port;

    /**
     * Database name.
     */
    private String database;

    /**
     * Username.
     */
    private String username;

    /**
     * Password.
     */
    private String password;

    /**
     * JDBC driver class name.
     */
    private String driverClassName;

    /**
     * JDBC URL.
     */
    private String url;

    /**
     * Additional properties.
     */
    private Map<String, String> properties;

    /**
     * Maximum connection pool size.
     */
    private Integer maxPoolSize;

    /**
     * Minimum idle connections.
     */
    private Integer minIdle;

    /**
     * Connection timeout.
     */
    private Integer connectionTimeout;

    /**
     * Auto-commit mode.
     */
    private Boolean autoCommit;

    /**
     * Use SSL.
     */
    private Boolean useSSL;

    /**
     * SSL mode.
     */
    private String sslMode;

    /**
     * Generates a connection identifier if none is provided.
     * Simplified implementation with clear patterns.
     *
     * @return The generated connection name
     */
    public String generateConnectionId() {
        // If a name is already provided, use it
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }

        StringBuilder builder = new StringBuilder();

        // Add database type if available
        String typePrefix = getDbTypePrefix();
        builder.append(typePrefix);

        // For URL-based connections, use a hash
        if (url != null && !url.isEmpty()) {
            String urlHash = Integer.toHexString(url.hashCode());
            builder.append("_url_").append(urlHash);
            return builder.toString();
        }

        // For host-based connections, use host information
        if (host != null && !host.isEmpty()) {
            // Replace dots with underscores for hostname
            String sanitizedHost = host.replace('.', '_');
            builder.append("_").append(sanitizedHost);

            // Add port if non-default
            if (port != null && !isDefaultPort(dbType, port)) {
                builder.append("_").append(port);
            }

            // Add database name if available
            if (database != null && !database.isEmpty()) {
                builder.append("_").append(database);
            }

            return builder.toString();
        }

        // Fallback to UUID if not enough information
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        builder.append("_").append(uniqueId);

        return builder.toString();
    }

    /**
     * Gets a prefix based on database type.
     *
     * @return Database type prefix
     */
    private String getDbTypePrefix() {
        if (dbType != null && !dbType.isEmpty()) {
            return dbType.toLowerCase();
        } else if (driverClassName != null) {
            // Extract db type from driver if possible
            if (driverClassName.contains("mysql")) {
                return "mysql";
            } else if (driverClassName.contains("postgresql")) {
                return "postgresql";
            } else if (driverClassName.contains("oracle")) {
                return "oracle";
            } else if (driverClassName.contains("sqlserver")) {
                return "sqlserver";
            }
        }

        return "db";
    }

    /**
     * Checks if a port is the default port for a database type.
     *
     * @param dbType Database type
     * @param port Port number
     * @return True if port is the default for the database type
     */
    private boolean isDefaultPort(String dbType, int port) {
        if (dbType == null) {
            return false;
        }

        return switch (dbType.toLowerCase()) {
            case "mysql" -> port == 3306;
            case "postgresql" -> port == 5432;
            case "oracle" -> port == 1521;
            case "sqlserver" -> port == 1433;
            default -> false;
        };
    }

    /**
     * Converts to DatabaseConnectionRequest.
     * Ensures connection name is automatically generated if not provided.
     *
     * @return DatabaseConnectionRequest
     */
    public DatabaseConnectionRequest toConnectionRequest() {
        // Ensure the name is set
        if (name == null || name.trim().isEmpty()) {
            name = generateConnectionId();
        }

        return DatabaseConnectionRequest.builder()
                .name(name)
                .dbType(dbType)
                .host(host)
                .port(port)
                .database(database)
                .username(username)
                .password(password)
                .driverClassName(driverClassName)
                .url(url)
                .properties(properties)
                .maxPoolSize(maxPoolSize)
                .minIdle(minIdle)
                .connectionTimeout(connectionTimeout)
                .autoCommit(autoCommit)
                .useSSL(useSSL)
                .sslMode(sslMode)
                .build();
    }
}