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
