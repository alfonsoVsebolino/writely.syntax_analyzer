package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code super} (or qualified {@code ClassName.super}) expression.
 */
public record JavaSuperExpression(
    Optional<JavaType> qualifier,
    SourceSpan span
) implements JavaExpression {
    public JavaSuperExpression {
        Objects.requireNonNull(qualifier, "qualifier must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaSuperExpression of(SourceSpan span) {
        return new JavaSuperExpression(Optional.empty(), span);
    }
}
