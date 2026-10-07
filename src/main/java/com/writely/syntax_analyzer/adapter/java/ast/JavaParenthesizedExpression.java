package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a parenthesized expression ({@code (expression)}).
 */
public record JavaParenthesizedExpression(
    JavaExpression expression,
    SourceSpan span
) implements JavaExpression {
    public JavaParenthesizedExpression {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
