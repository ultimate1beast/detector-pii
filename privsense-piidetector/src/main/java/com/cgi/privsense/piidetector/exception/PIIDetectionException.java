package com.cgi.privsense.piidetector.exception;

import com.cgi.privsense.common.exception.BaseException;

/**
 * Exception for PII detection errors.
 */
public class PIIDetectionException extends BaseException {
    private static final long serialVersionUID = 1L;

    // Standard error codes
    public static final String DETECTION_ERROR = "PII_DETECTION_ERROR";
    public static final String CONFIG_ERROR = "PII_CONFIG_ERROR";
    public static final String SERVICE_ERROR = "PII_SERVICE_ERROR";

    /**
     * Creates a new PII detection exception with the specified message.
     *
     * @param message Exception message
     */
    public PIIDetectionException(String message) {
        super(message, DETECTION_ERROR);
    }

    /**
     * Creates a new PII detection exception with the specified message and cause.
     *
     * @param message Exception message
     * @param cause The cause of the exception
     */
    public PIIDetectionException(String message, Throwable cause) {
        super(message, cause, DETECTION_ERROR);
    }

    /**
     * Creates a new PII detection exception with the specified message and error code.
     *
     * @param message Exception message
     * @param errorCode Error code
     */
    public PIIDetectionException(String message, String errorCode) {
        super(message, errorCode);
    }

    /**
     * Creates a new PII detection exception with the specified message, cause, and error code.
     *
     * @param message Exception message
     * @param cause The cause of the exception
     * @param errorCode Error code
     */
    public PIIDetectionException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode);
    }

    /**
     * Factory method for configuration errors.
     *
     * @param message Error message
     * @return PIIDetectionException with CONFIG_ERROR code
     */
    public static PIIDetectionException configError(String message) {
        return new PIIDetectionException(message, CONFIG_ERROR);
    }

    /**
     * Factory method for service errors.
     *
     * @param message Error message
     * @return PIIDetectionException with SERVICE_ERROR code
     */
    public static PIIDetectionException serviceError(String message) {
        return new PIIDetectionException(message, SERVICE_ERROR);
    }
}