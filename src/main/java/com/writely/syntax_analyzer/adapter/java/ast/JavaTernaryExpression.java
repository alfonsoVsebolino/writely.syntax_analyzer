package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a ternary conditional expression ({@code condition ? thenExpr : elseExpr}).
 */
public record JavaTernaryExpression(
    JavaExpression condition,
    JavaExpression thenExpr,
    JavaExpression elseExpr,
    SourceSpan span
) implements JavaExpression {
    public JavaTernaryExpression {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(thenExpr, "thenExpr must not be null");
        Objects.requireNonNull(elseExpr, "elseExpr must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
