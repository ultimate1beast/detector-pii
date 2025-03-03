package com.cgi.privsense.dbscanner.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.function.Function;

public abstract class AbstractDatabaseScanner implements DatabaseScanner {
    protected final JdbcTemplate jdbcTemplate;
    protected final String dbType;
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected AbstractDatabaseScanner(DataSource dataSource, String dbType) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.dbType = dbType;
        configureJdbcTemplate();
    }

    private void configureJdbcTemplate() {
        /*
         Configuration commune du JdbcTemplate
         */
        jdbcTemplate.setFetchSize(1000);
        jdbcTemplate.setMaxRows(10000);
    }

    @Override
    public String getDatabaseType() {
        return dbType;
    }

    /*
     Gestion commune des erreurs
     */
    protected <T> T executeQuery(String operation, Function<JdbcTemplate, T> query) {
        try {
            return query.apply(jdbcTemplate);
        } catch (DataAccessException e) {
            logger.error("Error executing {} : {}", operation, e.getMessage());
            throw new RuntimeException("Error during " + operation, e);
        }
    }

    /*
     Validation commune des paramètres
     */
    protected void validateTableName(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
    }

    /*
     Méthodes utilitaires communes
     */
    protected boolean tableExists(String tableName) {
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM " + tableName + " WHERE 1 = 0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
