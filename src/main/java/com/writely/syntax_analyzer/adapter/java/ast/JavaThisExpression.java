package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code this} (or qualified {@code ClassName.this}) expression.
 */
public record JavaThisExpression(
    Optional<JavaType> qualifier,
    SourceSpan span
) implements JavaExpression {
    public JavaThisExpression {
        Objects.requireNonNull(qualifier, "qualifier must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaThisExpression of(SourceSpan span) {
        return new JavaThisExpression(Optional.empty(), span);
    }
}
