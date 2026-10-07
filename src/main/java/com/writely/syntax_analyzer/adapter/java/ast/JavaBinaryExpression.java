package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents an infix binary operator expression (arithmetic, comparison, bitwise, logical).
 */
public record JavaBinaryExpression(
    JavaExpression left,
    String operator,
    JavaExpression right,
    SourceSpan span
) implements JavaExpression {
    public JavaBinaryExpression {
        Objects.requireNonNull(left, "left must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(right, "right must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
