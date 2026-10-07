package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a type cast expression ({@code (Type) expr}).
 */
public record JavaCastExpression(
    JavaType type,
    JavaExpression expression,
    SourceSpan span
) implements JavaExpression {
    public JavaCastExpression {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
