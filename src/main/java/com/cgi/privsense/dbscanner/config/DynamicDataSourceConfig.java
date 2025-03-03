package com.cgi.privsense.dbscanner.config;

import com.cgi.privsense.dbscanner.config.dtoconfig.DatabaseConnectionRequest;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class DynamicDataSourceConfig {

    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
    private final RoutingDataSource routingDataSource;
    private final DriverDependencyManager driverManager;

    public DynamicDataSourceConfig(DriverDependencyManager driverManager) {
        this.driverManager = driverManager;
        this.routingDataSource = new RoutingDataSource();
        this.routingDataSource.setTargetDataSources(new HashMap<>());
        this.routingDataSource.setDefaultTargetDataSource(new EmptyDataSource());
        this.routingDataSource.afterPropertiesSet();
    }

    public DataSource createDataSource(DatabaseConnectionRequest request) {
        /*
         S'assurer que le driver est disponible
         */
        String driverClassName = request.getDriverClassName();
        if (driverClassName == null) {
            driverClassName = getDriverClassName(request.getDbType());
        }
        driverManager.ensureDriverAvailable(driverClassName);

        DataSourceBuilder<?> builder = DataSourceBuilder.create();

        /*
         Construction de l'URL si non fournie
         */
        String url = request.getUrl();
        if (url == null) {
            url = buildJdbcUrl(request);
        }

        /*
         Configuration de base
         */
        builder.url(url)
                .username(request.getUsername())
                .password(request.getPassword())
                .driverClassName(driverClassName);

        /*
         Création de la source de données Hikari
         */
        HikariDataSource dataSource = (HikariDataSource) builder.type(HikariDataSource.class).build();

        /*
         Configuration du pool
         */
        if (request.getMaxPoolSize() != null) {
            dataSource.setMaximumPoolSize(request.getMaxPoolSize());
        }
        if (request.getMinIdle() != null) {
            dataSource.setMinimumIdle(request.getMinIdle());
        }
        if (request.getConnectionTimeout() != null) {
            dataSource.setConnectionTimeout(request.getConnectionTimeout());
        }
        if (request.getAutoCommit() != null) {
            dataSource.setAutoCommit(request.getAutoCommit());
        }

        /*
         Ajout des propriétés additionnelles
         */
        if (request.getProperties() != null) {
            dataSource.setDataSourceProperties(new Properties() {{
                putAll(request.getProperties());
            }});
        }

        registerDataSource(request.getName(), dataSource);
        return dataSource;
    }

    private String buildJdbcUrl(DatabaseConnectionRequest request) {
        return switch (request.getDbType().toLowerCase()) {
            case "mysql" -> String.format(
                    "jdbc:mysql://%s:%d/%s",
                    request.getHost(),
                    request.getPort() != null ? request.getPort() : 3306,
                    request.getDatabase()
            );
            case "postgresql" -> String.format(
                    "jdbc:postgresql://%s:%d/%s",
                    request.getHost(),
                    request.getPort() != null ? request.getPort() : 5432,
                    request.getDatabase()
            );
            /*
             Ajouter d'autres types de bases de données ici
             */
            default -> throw new IllegalArgumentException("Unsupported database type: " + request.getDbType());
        };
    }

    public void registerDataSource(String name, DataSource dataSource) {
        dataSources.put(name, dataSource);
        updateRoutingDataSource();
    }

    private void updateRoutingDataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>(dataSources);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();
    }

    @Bean
    @Primary
    public DataSource routingDataSource() {
        return routingDataSource;
    }

    private String getDriverClassName(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql" -> "org.postgresql.Driver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        };
    }

    public DataSource getDataSource(String name) {
        RoutingDataSource.setCurrentDataSource(name);
        return routingDataSource;
    }
}
