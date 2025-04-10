package com.cgi.privsense.piidetector.api;

import com.cgi.privsense.piidetector.model.TablePIIInfo;

/**
 * Service interface for PII detection operations at the table level.
 * Separates table-specific PII detection functionality for better maintainability.
 */
public interface TablePIIService {
    
    /**
     * Detects PII in a specific table.
     *
     * @param connectionId Database connection identifier
     * @param dbType Database type
     * @param tableName Name of the table to analyze
     * @return Information about PIIs found in the table
     */
    TablePIIInfo detectPIIInTable(String connectionId, String dbType, String tableName);
}