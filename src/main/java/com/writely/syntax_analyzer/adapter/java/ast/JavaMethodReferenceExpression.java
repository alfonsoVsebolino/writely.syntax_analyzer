package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a method reference expression (e.g. {@code String::valueOf} or {@code this::process}).
 */
public record JavaMethodReferenceExpression(
    JavaExpression target,
    String methodName,
    SourceSpan span
) implements JavaExpression {
    public JavaMethodReferenceExpression {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(methodName, "methodName must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
