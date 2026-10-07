package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a {@code throw} statement.
 */
public record JavaThrowStatement(
    JavaExpression expression,
    SourceSpan span
) implements JavaStatement {
    public JavaThrowStatement {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
