// Simplified unified exception for database operations
package com.cgi.privsense.dbscanner.exception;

import com.cgi.privsense.common.exception.BaseException;

/**
 * Unified exception for all database operations, consolidating multiple specialized exceptions.
 * Uses error codes to differentiate between different error types.
 */
public class DatabaseOperationException extends BaseException {
    private static final long serialVersionUID = 1L;

    // Error codes
    public static final String SCANNER_ERROR = "SCANNER_ERROR";
    public static final String DATASOURCE_ERROR = "DATASOURCE_ERROR";
    public static final String DRIVER_ERROR = "DRIVER_ERROR";
    public static final String SAMPLING_ERROR = "SAMPLING_ERROR";
    public static final String CONNECTION_ERROR = "CONNECTION_ERROR";

    /**
     * Creates a new DatabaseOperationException with the specified message and error code.
     *
     * @param message Exception message
     * @param errorCode Error code
     */
    public DatabaseOperationException(String message, String errorCode) {
        super(message, errorCode);
    }

    /**
     * Creates a new DatabaseOperationException with the specified message, cause, and error code.
     *
     * @param message Exception message
     * @param cause The cause of the exception
     * @param errorCode Error code
     */
    public DatabaseOperationException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode);
    }

    /**
     * Factory method for scanner errors.
     *
     * @param message Exception message
     * @return DatabaseOperationException with SCANNER_ERROR code
     */
    public static DatabaseOperationException scannerError(String message) {
        return new DatabaseOperationException(message, SCANNER_ERROR);
    }

    /**
     * Factory method for scanner errors with cause.
     *
     * @param message Exception message
     * @param cause The cause of the exception
     * @return DatabaseOperationException with SCANNER_ERROR code
     */
    public static DatabaseOperationException scannerError(String message, Throwable cause) {
        return new DatabaseOperationException(message, cause, SCANNER_ERROR);
    }

    /**
     * Factory method for data source errors.
     *
     * @param message Exception message
     * @return DatabaseOperationException with DATASOURCE_ERROR code
     */
    public static DatabaseOperationException dataSourceError(String message) {
        return new DatabaseOperationException(message, DATASOURCE_ERROR);
    }

    /**
     * Factory method for data source errors with cause.
     *
     * @param message Exception message
     * @param cause The cause of the exception
     * @return DatabaseOperationException with DATASOURCE_ERROR code
     */
    public static DatabaseOperationException dataSourceError(String message, Throwable cause) {
        return new DatabaseOperationException(message, cause, DATASOURCE_ERROR);
    }

    /**
     * Factory method for driver errors.
     *
     * @param message Exception message
     * @return DatabaseOperationException with DRIVER_ERROR code
     */
    public static DatabaseOperationException driverError(String message) {
        return new DatabaseOperationException(message, DRIVER_ERROR);
    }

    /**
     * Factory method for driver errors with cause.
     *
     * @param message Exception message
     * @param cause The cause of the exception
     * @return DatabaseOperationException with DRIVER_ERROR code
     */
    public static DatabaseOperationException driverError(String message, Throwable cause) {
        return new DatabaseOperationException(message, cause, DRIVER_ERROR);
    }

    /**
     * Factory method for sampling errors.
     *
     * @param message Exception message
     * @return DatabaseOperationException with SAMPLING_ERROR code
     */
    public static DatabaseOperationException samplingError(String message) {
        return new DatabaseOperationException(message, SAMPLING_ERROR);
    }

    /**
     * Factory method for sampling errors with cause.
     *
     * @param message Exception message
     * @param cause The cause of the exception
     * @return DatabaseOperationException with SAMPLING_ERROR code
     */
    public static DatabaseOperationException samplingError(String message, Throwable cause) {
        return new DatabaseOperationException(message, cause, SAMPLING_ERROR);
    }

    /**
     * Factory method for connection errors.
     *
     * @param message Exception message
     * @return DatabaseOperationException with CONNECTION_ERROR code
     */
    public static DatabaseOperationException connectionError(String message) {
        return new DatabaseOperationException(message, CONNECTION_ERROR);
    }

    /**
     * Factory method for connection errors with cause.
     *
     * @param message Exception message
     * @param cause The cause of the exception
     * @return DatabaseOperationException with CONNECTION_ERROR code
     */
    public static DatabaseOperationException connectionError(String message, Throwable cause) {
        return new DatabaseOperationException(message, cause, CONNECTION_ERROR);
    }
}