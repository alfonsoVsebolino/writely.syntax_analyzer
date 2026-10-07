package com.writely.syntax_analyzer.domain;

/**
 * Source code ingestion modes supported by the analyzer.
 */
public enum InputMode {
    SINGLE_LINE("Single Line"),
    CODE_SNIPPET("Code Snippet"),
    FILE_UPLOAD("File Upload");

    private final String displayName;

    InputMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
