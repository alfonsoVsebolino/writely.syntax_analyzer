package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents an enhanced {@code for-each} loop.
 */
public record JavaEnhancedForStatement(
    JavaParameter variable,
    JavaExpression expression,
    JavaStatement body,
    SourceSpan span
) implements JavaStatement {
    public JavaEnhancedForStatement {
        Objects.requireNonNull(variable, "variable must not be null");
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
