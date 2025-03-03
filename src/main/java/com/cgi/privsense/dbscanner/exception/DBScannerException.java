package com.cgi.privsense.dbscanner.exception;

public class DBScannerException extends RuntimeException {
    public DBScannerException(String message) {
        super(message);
    }

    public DBScannerException(String message, Throwable cause) {
        super(message, cause);
    }


}
