package com.writely.syntax_analyzer.domain;

/**
 * Diagnostic severity levels.
 */
public enum Severity {
    ERROR,
    WARNING,
    INFO;

    public boolean isError() {
        return this == ERROR;
    }

    public boolean isWarning() {
        return this == WARNING;
    }

    public boolean isInfo() {
        return this == INFO;
    }
}
