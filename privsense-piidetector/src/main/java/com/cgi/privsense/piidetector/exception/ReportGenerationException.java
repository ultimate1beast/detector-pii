package com.cgi.privsense.piidetector.exception;

import com.cgi.privsense.common.exception.BaseException;

/**
 * Exception for report generation and export errors.
 */
public class ReportGenerationException extends BaseException {
    private static final long serialVersionUID = 1L;

    // Standard error codes
    public static final String EXPORT_ERROR = "REPORT_EXPORT_ERROR";
    public static final String FORMAT_ERROR = "REPORT_FORMAT_ERROR";

    /**
     * Creates a new report generation exception with the specified message.
     *
     * @param message Exception message
     */
    public ReportGenerationException(String message) {
        super(message, EXPORT_ERROR);
    }

    /**
     * Creates a new report generation exception with the specified message and cause.
     *
     * @param message Exception message
     * @param cause The cause of the exception
     */
    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause, EXPORT_ERROR);
    }

    /**
     * Creates a new report generation exception with the specified message and error code.
     *
     * @param message Exception message
     * @param errorCode The error code
     */
    public ReportGenerationException(String message, String errorCode) {
        super(message, errorCode);
    }

    /**
     * Creates a new report generation exception for format errors.
     *
     * @param format The invalid format
     * @return A new exception instance
     */
    public static ReportGenerationException formatError(String format) {
        return new ReportGenerationException("Unsupported export format: " + format, FORMAT_ERROR);
    }
}