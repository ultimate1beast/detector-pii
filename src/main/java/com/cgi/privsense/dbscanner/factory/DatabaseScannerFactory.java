package com.cgi.privsense.dbscanner.factory;

import com.cgi.privsense.dbscanner.core.DatabaseScanner;
import com.cgi.privsense.dbscanner.core.DatabaseType;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DatabaseScannerFactory {
    private final Map<String, Class<? extends DatabaseScanner>> scannerTypes;

    /*
     Trouver tous les scanners avec l'annotation @DatabaseType
     */
    public DatabaseScannerFactory(ApplicationContext applicationContext) {

        this.scannerTypes = applicationContext.getBeansOfType(DatabaseScanner.class)
                .values()
                .stream()
                .collect(Collectors.toMap(
                        scanner -> scanner.getClass()
                                .getAnnotation(DatabaseType.class)
                                .value()
                                .toLowerCase(),
                        DatabaseScanner::getClass
                ));
    }

    public DatabaseScanner getScanner(String dbType, DataSource dataSource) {
        Class<? extends DatabaseScanner> scannerClass = scannerTypes.get(dbType.toLowerCase());
        if (scannerClass == null) {
            throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }

        /*
         Créer une nouvelle instance du scanner avec le DataSource
         */
        try {
            return scannerClass.getConstructor(DataSource.class)
                    .newInstance(dataSource);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create scanner for type: " + dbType, e);
        }
    }

    public List<String> getSupportedDatabaseTypes() {
        return List.copyOf(scannerTypes.keySet());
    }
}
