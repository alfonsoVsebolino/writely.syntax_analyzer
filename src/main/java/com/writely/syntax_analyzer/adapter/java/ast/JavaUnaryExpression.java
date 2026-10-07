package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a unary prefix or postfix expression (e.g. {@code -x}, {@code !flag}, {@code count++}).
 */
public record JavaUnaryExpression(
    String operator,
    JavaExpression operand,
    boolean isPrefix,
    SourceSpan span
) implements JavaExpression {
    public JavaUnaryExpression {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(operand, "operand must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
