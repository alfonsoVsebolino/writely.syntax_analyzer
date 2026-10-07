package com.writely.syntax_analyzer.core.ingestion;

/**
 * Categorical error codes for source code ingestion failures.
 */
public enum IngestionErrorCode {
    EMPTY_SOURCE_INPUT("Source input is null, empty, or whitespace-only"),
    LANGUAGE_REQUIRED("Language could not be inferred and was not explicitly selected"),
    FILE_NOT_FOUND("Specified file path does not exist"),
    FILE_UNREADABLE("File is not a regular readable file or permissions prevent reading"),
    FILE_TOO_LARGE("File exceeds maximum allowed size limit"),
    BINARY_FILE_DETECTED("Binary content or invalid UTF-8 sequences detected"),
    IO_ERROR("An underlying I/O error occurred while reading the file");

    private final String description;

    IngestionErrorCode(String description) {
        this.description = description;
    }

    /**
     * Human-readable description of the error code.
     */
    public String description() {
        return description;
    }
}
