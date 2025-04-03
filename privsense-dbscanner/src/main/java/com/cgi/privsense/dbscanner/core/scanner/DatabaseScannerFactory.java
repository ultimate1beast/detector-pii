package com.cgi.privsense.dbscanner.core.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Simplified factory for creating database scanners with improved error handling.
 */
@Component
public class DatabaseScannerFactory {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseScannerFactory.class);

    /**
     * Map of database types to scanner classes.
     */
    private final Map<String, Class<? extends DatabaseScanner>> scannerTypes;

    /**
     * Spring application context for bean creation.
     */
    private final ApplicationContext applicationContext;

    /**
     * Constructor.
     * Finds all scanners with the @DatabaseType annotation.
     *
     * @param applicationContext Spring application context
     */
    public DatabaseScannerFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;

        Map<String, DatabaseScanner> scannerBeans = applicationContext.getBeansOfType(DatabaseScanner.class);

        this.scannerTypes = scannerBeans.values().stream()
                .filter(scanner -> scanner.getClass().isAnnotationPresent(DatabaseType.class))
                .collect(Collectors.toMap(
                        scanner -> scanner.getClass()
                                .getAnnotation(DatabaseType.class)
                                .value()
                                .toLowerCase(),
                        DatabaseScanner::getClass
                ));

        logger.info("Registered database scanners: {}", scannerTypes.keySet());
    }

    /**
     * Gets a scanner for the specified database type.
     * Improved error handling and fallback to default scanner if specified type not found.
     *
     * @param dbType Database type
     * @param dataSource Data source
     * @return Database scanner
     */
    public DatabaseScanner getScanner(String dbType, DataSource dataSource) {
        if (dbType == null || dbType.isEmpty()) {
            logger.warn("No database type specified, defaulting to MySQL");
            dbType = "mysql";
        }

        String normalizedDbType = dbType.toLowerCase();

        // First look for exact match
        Class<? extends DatabaseScanner> scannerClass = scannerTypes.get(normalizedDbType);

        // If not found, try to find a partial match
        if (scannerClass == null) {
            Optional<String> matchingType = scannerTypes.keySet().stream()
                    .filter(type -> normalizedDbType.contains(type) || type.contains(normalizedDbType))
                    .findFirst();

            if (matchingType.isPresent()) {
                String matchedType = matchingType.get();
                logger.info("No exact match for '{}', using closest match: '{}'", dbType, matchedType);
                scannerClass = scannerTypes.get(matchedType);
            }
        }

        // If still not found, and MySQL is available, use that as default
        if (scannerClass == null && scannerTypes.containsKey("mysql")) {
            logger.warn("Unsupported database type: {}, defaulting to MySQL", dbType);
            scannerClass = scannerTypes.get("mysql");
        }

        // If nothing is available, throw a helpful exception
        if (scannerClass == null) {
            throw new IllegalArgumentException(
                    "Unsupported database type: " + dbType +
                            ". Supported types: " + String.join(", ", scannerTypes.keySet()));
        }

        // Create a new instance of the scanner with the data source
        try {
            logger.debug("Creating scanner of type {} for database type: {}", scannerClass.getSimpleName(), dbType);
            return scannerClass.getConstructor(DataSource.class)
                    .newInstance(dataSource);
        } catch (Exception e) {
            logger.error("Failed to create scanner for type: {}", dbType, e);
            throw new RuntimeException("Failed to create scanner for type: " + dbType, e);
        }
    }

    /**
     * Gets the list of supported database types.
     *
     * @return List of supported database types
     */
    public List<String> getSupportedDatabaseTypes() {
        return Collections.unmodifiableList(scannerTypes.keySet().stream().sorted().toList());
    }
}