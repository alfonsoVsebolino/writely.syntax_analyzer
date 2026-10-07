package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code continue} statement with an optional label.
 */
public record JavaContinueStatement(
    Optional<String> label,
    SourceSpan span
) implements JavaStatement {
    public JavaContinueStatement {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaContinueStatement of(SourceSpan span) {
        return new JavaContinueStatement(Optional.empty(), span);
    }

    public static JavaContinueStatement of(String label, SourceSpan span) {
        return new JavaContinueStatement(Optional.ofNullable(label), span);
    }
}
