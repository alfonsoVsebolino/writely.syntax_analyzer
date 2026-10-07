package com.writely.syntax_analyzer.domain;

/**
 * High-level status of the syntactical analysis outcome.
 */
public enum AnalysisStatus {
    PASSED,
    FAILED_SYNTAX_ERRORS;

    public boolean isPassed() {
        return this == PASSED;
    }

    public boolean hasErrors() {
        return this == FAILED_SYNTAX_ERRORS;
    }
}
