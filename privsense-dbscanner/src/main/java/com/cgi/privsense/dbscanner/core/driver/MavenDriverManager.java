package com.cgi.privsense.dbscanner.core.driver;

import com.cgi.privsense.common.config.properties.DatabaseProperties;
import com.cgi.privsense.dbscanner.exception.DatabaseOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of DriverManager that downloads drivers from Maven Central.
 * Optimized with better error handling and thread safety.
 */
@Slf4j
@Component
public class MavenDriverManager implements DriverManager {
    /**
     * Map of driver class names to Maven coordinates.
     */
    private final Map<String, String> driverMavenCoordinates;

    /**
     * Map of loaded drivers.
     */
    private final Map<String, Driver> loadedDrivers = new ConcurrentHashMap<>();

    /**
     * Directory where downloaded drivers are stored.
     */
    private final Path driversDir;

    /**
     * Maven repository URL.
     */
    private final String repositoryUrl;

    /**
     * Lock for thread-safe driver loading.
     */
    private final ReentrantReadWriteLock driversLock = new ReentrantReadWriteLock();

    /**
     * Constructor with GlobalProperties for centralized configuration.
     *
     * @param properties Global application properties
     */
    public MavenDriverManager(DatabaseProperties databaseProperties) {
        // Get driver properties from the database properties
        DatabaseProperties.DriverProperties driverProps = databaseProperties.getDrivers();

        this.driverMavenCoordinates = driverProps.getCoordinates();
        this.driversDir = Paths.get(driverProps.getDirectory());
        this.repositoryUrl = driverProps.getRepositoryUrl();

        try {
            // Create the drivers directory if it doesn't exist
            Files.createDirectories(driversDir);
            log.info("Driver directory initialized at {}", driversDir);
        } catch (Exception e) {
            log.error("Failed to create drivers directory: {}", driversDir, e);
            throw DatabaseOperationException.driverError("Failed to create drivers directory", e);
        }
    }

    @Override
    public void ensureDriverAvailable(String driverClassName) {
        if (driverClassName == null || driverClassName.isEmpty()) {
            throw DatabaseOperationException.driverError("Driver class name cannot be null or empty");
        }

        // First check without locking
        if (isDriverLoaded(driverClassName)) {
            log.debug("Driver already loaded: {}", driverClassName);
            return;
        }

        // Skip the separate readLock section and go directly to the writeLock
        driversLock.writeLock().lock();
        try {
            // Double-check after acquiring the write lock
            if (isDriverLoaded(driverClassName)) {
                log.debug("Driver already loaded (detected after write lock): {}", driverClassName);
                return;
            }

            String mavenCoordinates = driverMavenCoordinates.get(driverClassName);
            if (mavenCoordinates == null) {
                throw DatabaseOperationException.driverError("Unknown driver: " + driverClassName +
                        ". Add Maven coordinates to configuration properties.");
            }

            // Download and load the driver
            Path driverJar = downloadDriver(mavenCoordinates);
            loadDriver(driverClassName, driverJar);

            log.info("Successfully loaded driver: {}", driverClassName);
        } catch (Exception e) {
            log.error("Failed to load driver: {}", driverClassName, e);
            throw DatabaseOperationException.driverError("Failed to load driver: " + driverClassName, e);
        } finally {
            driversLock.writeLock().unlock();
        }
    }

    @Override
    public boolean isDriverLoaded(String driverClassName) {
        // First check the cache of already loaded drivers
        if (loadedDrivers.containsKey(driverClassName)) {
            return true;
        }

        // Then check if the class is available in the classpath
        try {
            Class.forName(driverClassName);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public Driver getDriver(String driverClassName) {
        if (driverClassName == null || driverClassName.isEmpty()) {
            throw DatabaseOperationException.driverError("Driver class name cannot be null or empty");
        }

        // Ensure driver is loaded
        if (!isDriverLoaded(driverClassName)) {
            ensureDriverAvailable(driverClassName);
        }

        return loadedDrivers.get(driverClassName);
    }

    /**
     * Downloads a driver JAR from Maven Central with improved error handling.
     *
     * @param mavenCoordinates Maven coordinates of the driver
     * @return Path to the downloaded JAR
     */
    private Path downloadDriver(String mavenCoordinates) {
        if (mavenCoordinates == null || mavenCoordinates.isEmpty() || !mavenCoordinates.contains(":")) {
            throw DatabaseOperationException.driverError("Invalid Maven coordinates format: " + mavenCoordinates);
        }

        String[] parts = mavenCoordinates.split(":");
        if (parts.length < 3) {
            throw DatabaseOperationException.driverError(
                    "Maven coordinates must have at least groupId, artifactId and version: " + mavenCoordinates);
        }

        String groupId = parts[0].replace('.', '/');
        String artifactId = parts[1];
        String version = parts[2];

        Path jarPath = driversDir.resolve(artifactId + "-" + version + ".jar");

        // Check if jar already exists
        if (Files.exists(jarPath)) {
            log.debug("Driver jar already exists: {}", jarPath);
            return jarPath;
        }

        // Build the Maven repository URI
        URI uri = URI.create(String.format(
                "%s/%s/%s/%s/%s-%s.jar",
                repositoryUrl, groupId, artifactId, version, artifactId, version));

        try {
            log.info("Downloading driver from: {}", uri);

            // Create temporary file first
            Path tempFile = Files.createTempFile(driversDir, "downloading-", ".tmp");

            // Download to temporary file
            Files.copy(uri.toURL().openStream(), tempFile);

            // Move to final location atomically
            Files.move(tempFile, jarPath);

            log.info("Driver downloaded successfully: {}", jarPath);
            return jarPath;
        } catch (Exception e) {
            throw DatabaseOperationException.driverError("Failed to download driver: " + uri, e);
        }
    }

    /**
     * Loads a driver from a JAR file with improved error handling.
     *
     * @param driverClassName Name of the driver class
     * @param jarPath         Path to the JAR file
     */
    private void loadDriver(String driverClassName, Path jarPath) {
        try {
            // Create a URL class loader with the driver JAR
            URLClassLoader classLoader = new URLClassLoader(
                    new URL[] { jarPath.toUri().toURL() },
                    getClass().getClassLoader());

            // Load and instantiate the driver
            Class<?> driverClass = Class.forName(driverClassName, true, classLoader);
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();

            // Register the driver with a shim to avoid classloader issues
            java.sql.DriverManager.registerDriver(new DriverShim(driver));

            // Store the driver in our cache
            loadedDrivers.put(driverClassName, driver);
            log.info("Successfully loaded driver: {}", driverClassName);

        } catch (ClassNotFoundException e) {
            throw DatabaseOperationException.driverError(
                    "Driver class not found: " + driverClassName + ". Check Maven coordinates.", e);
        } catch (Exception e) {
            throw DatabaseOperationException.driverError("Failed to load driver class: " + driverClassName, e);
        }
    }
}