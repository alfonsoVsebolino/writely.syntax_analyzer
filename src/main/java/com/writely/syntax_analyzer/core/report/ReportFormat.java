package com.writely.syntax_analyzer.core.report;

/**
 * Supported report export formats.
 */
public enum ReportFormat {
    TEXT("txt", "text/plain", "Plain Text"),
    JSON("json", "application/json", "JSON");

    private final String extension;
    private final String mimeType;
    private final String displayName;

    ReportFormat(String extension, String mimeType, String displayName) {
        this.extension = extension;
        this.mimeType = mimeType;
        this.displayName = displayName;
    }

    /**
     * Canonical file extension without leading dot.
     */
    public String extension() {
        return extension;
    }

    /**
     * MIME media type for the format.
     */
    public String mimeType() {
        return mimeType;
    }

    /**
     * Human-readable display label.
     */
    public String displayName() {
        return displayName;
    }
}
