package com.writely.syntax_analyzer.core.ingestion;

import java.util.Objects;

/**
 * Unchecked exception thrown when source code ingestion fails.
 * Carries an {@link IngestionErrorCode} identifying the root failure category.
 */
public class IngestionException extends RuntimeException {

    private final IngestionErrorCode errorCode;

    public IngestionException(IngestionErrorCode errorCode) {
        this(errorCode, Objects.requireNonNull(errorCode, "errorCode must not be null").description());
    }

    public IngestionException(IngestionErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public IngestionException(IngestionErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public IngestionException(String message) {
        this(resolveErrorCode(message), message, null);
    }

    public IngestionException(String message, Throwable cause) {
        this(resolveErrorCode(message), message, cause);
    }

    private static IngestionErrorCode resolveErrorCode(String message) {
        if (message != null) {
            if (message.contains("10 MB limit") || message.contains("limit") || message.contains("exceeds")) {
                return IngestionErrorCode.FILE_TOO_LARGE;
            }
            if (message.contains("Binary") || message.contains("UTF-8")) {
                return IngestionErrorCode.BINARY_FILE_DETECTED;
            }
        }
        return IngestionErrorCode.IO_ERROR;
    }

    /**
     * Returns the error code indicating the ingestion failure category.
     */
    public IngestionErrorCode errorCode() {
        return errorCode;
    }

    /**
     * JavaBean-compatible getter for the error code.
     */
    public IngestionErrorCode getErrorCode() {
        return errorCode;
    }
}
