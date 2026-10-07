package com.writely.syntax_analyzer.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Diagnostic finding produced by syntactic inspection.
 */
public record Diagnostic(
    CheckCategory category,
    Severity severity,
    SourceLocation location,
    String message,
    String code,
    Optional<String> suggestedFix
) {
    public Diagnostic {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(suggestedFix, "suggestedFix must not be null");
    }

    public static Diagnostic of(
        CheckCategory category,
        Severity severity,
        SourceLocation location,
        String message,
        String code
    ) {
        return new Diagnostic(category, severity, location, message, code, Optional.empty());
    }

    public static Diagnostic of(
        CheckCategory category,
        Severity severity,
        SourceLocation location,
        String message,
        String code,
        String suggestedFix
    ) {
        return new Diagnostic(category, severity, location, message, code, Optional.ofNullable(suggestedFix));
    }

    public static Diagnostic error(
        CheckCategory category,
        SourceLocation location,
        String message,
        String code
    ) {
        return of(category, Severity.ERROR, location, message, code);
    }

    public static Diagnostic error(
        CheckCategory category,
        SourceLocation location,
        String message,
        String code,
        String suggestedFix
    ) {
        return of(category, Severity.ERROR, location, message, code, suggestedFix);
    }

    public static Diagnostic warning(
        CheckCategory category,
        SourceLocation location,
        String message,
        String code
    ) {
        return of(category, Severity.WARNING, location, message, code);
    }

    public static Diagnostic warning(
        CheckCategory category,
        SourceLocation location,
        String message,
        String code,
        String suggestedFix
    ) {
        return of(category, Severity.WARNING, location, message, code, suggestedFix);
    }

    public static Diagnostic info(
        CheckCategory category,
        SourceLocation location,
        String message,
        String code
    ) {
        return of(category, Severity.INFO, location, message, code);
    }

    public static Diagnostic info(
        CheckCategory category,
        SourceLocation location,
        String message,
        String code,
        String suggestedFix
    ) {
        return of(category, Severity.INFO, location, message, code, suggestedFix);
    }

    public boolean isError() {
        return severity.isError();
    }
}
