package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a {@code do-while} loop statement.
 */
public record JavaDoWhileStatement(
    JavaStatement body,
    JavaExpression condition,
    SourceSpan span
) implements JavaStatement {
    public JavaDoWhileStatement {
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
