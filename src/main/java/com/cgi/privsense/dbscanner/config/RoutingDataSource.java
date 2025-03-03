package com.cgi.privsense.dbscanner.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class RoutingDataSource extends AbstractRoutingDataSource {

    private static final ThreadLocal<String> currentDataSource = new ThreadLocal<>();

    public static void setCurrentDataSource(String dataSourceName) {
        currentDataSource.set(dataSourceName);
    }

    public static void clearCurrentDataSource() {
        currentDataSource.remove();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return currentDataSource.get();
    }
}
