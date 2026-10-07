package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a simple identifier reference expression.
 */
public record JavaIdentifierExpression(
    String name,
    SourceSpan span
) implements JavaExpression {
    public JavaIdentifierExpression {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
