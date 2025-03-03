package com.cgi.privsense.dbscanner.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DriverDependencyManager {
    private final Map<String, String> driverMavenCoordinates = Map.of(
            "oracle.jdbc.OracleDriver", "com.oracle.database.jdbc:ojdbc8:23.3.0",
            "com.mysql.cj.jdbc.Driver", "mysql:mysql-connector-java:8.0.33",
            "org.postgresql.Driver", "org.postgresql:postgresql:42.7.1"
    );

    private final Map<String, Driver> loadedDrivers = new ConcurrentHashMap<>();
    private final Path driversDir = Paths.get(System.getProperty("user.home"), ".dbscanner", "drivers");

    public DriverDependencyManager() {
        try {
            Files.createDirectories(driversDir);
        } catch (Exception e) {
            log.error("Failed to create drivers directory", e);
        }
    }

    public void ensureDriverAvailable(String driverClassName) {
        if (isDriverLoaded(driverClassName)) {
            return;
        }

        try {
            String mavenCoordinates = driverMavenCoordinates.get(driverClassName);
            if (mavenCoordinates == null) {
                throw new IllegalArgumentException("Unknown driver: " + driverClassName);
            }

            Path driverJar = downloadDriver(mavenCoordinates);
            loadDriver(driverClassName, driverJar);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load driver: " + driverClassName, e);
        }
    }

    private boolean isDriverLoaded(String driverClassName) {
        try {
            Class.forName(driverClassName);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private Path downloadDriver(String mavenCoordinates) {
        String[] parts = mavenCoordinates.split(":");
        String groupId = parts[0].replace('.', '/');
        String artifactId = parts[1];
        String version = parts[2];

        Path jarPath = driversDir.resolve(artifactId + "-" + version + ".jar");

        if (Files.exists(jarPath)) {
            return jarPath;
        }

        URI uri = URI.create(String.format(
                "https://repo1.maven.org/maven2/%s/%s/%s/%s-%s.jar",
                groupId, artifactId, version, artifactId, version
        ));

        try {
            log.info("Downloading driver from: {}", uri);
            Files.copy(uri.toURL().openStream(), jarPath);
            return jarPath;
        } catch (Exception e) {
            throw new RuntimeException("Failed to download driver: " + uri, e);
        }
    }

    private void loadDriver(String driverClassName, Path jarPath) {
        try {
            URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{jarPath.toUri().toURL()},
                    getClass().getClassLoader()
            );

            Class<?> driverClass = Class.forName(driverClassName, true, classLoader);
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(driver));

            loadedDrivers.put(driverClassName, driver);
            log.info("Successfully loaded driver: {}", driverClassName);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load driver class: " + driverClassName, e);
        }
    }
}
