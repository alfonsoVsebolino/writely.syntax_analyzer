package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code break} statement with an optional label.
 */
public record JavaBreakStatement(
    Optional<String> label,
    SourceSpan span
) implements JavaStatement {
    public JavaBreakStatement {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaBreakStatement of(SourceSpan span) {
        return new JavaBreakStatement(Optional.empty(), span);
    }

    public static JavaBreakStatement of(String label, SourceSpan span) {
        return new JavaBreakStatement(Optional.ofNullable(label), span);
    }
}
