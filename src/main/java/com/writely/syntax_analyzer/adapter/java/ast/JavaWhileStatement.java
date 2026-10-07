package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a {@code while} loop statement.
 */
public record JavaWhileStatement(
    JavaExpression condition,
    JavaStatement body,
    SourceSpan span
) implements JavaStatement {
    public JavaWhileStatement {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
