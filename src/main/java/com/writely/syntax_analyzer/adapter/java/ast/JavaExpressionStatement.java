package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a statement consisting of a single expression followed by a semicolon.
 */
public record JavaExpressionStatement(
    JavaExpression expression,
    SourceSpan span
) implements JavaStatement {
    public JavaExpressionStatement {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
