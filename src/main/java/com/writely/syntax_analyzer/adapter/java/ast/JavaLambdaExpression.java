package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a lambda expression (e.g. {@code (x) -> x + 1} or {@code () -> { ... }}).
 */
public record JavaLambdaExpression(
    List<JavaParameter> parameters,
    JavaAstNode body,
    SourceSpan span
) implements JavaExpression {
    public JavaLambdaExpression {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
