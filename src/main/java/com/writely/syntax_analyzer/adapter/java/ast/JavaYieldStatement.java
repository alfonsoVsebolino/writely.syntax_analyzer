package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a {@code yield} statement yielding a value from a switch expression block.
 */
public record JavaYieldStatement(
    JavaExpression expression,
    SourceSpan span
) implements JavaStatement {
    public JavaYieldStatement {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
