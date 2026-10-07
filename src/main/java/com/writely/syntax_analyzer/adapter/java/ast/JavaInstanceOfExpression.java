package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a type comparison expression with optional pattern variable ({@code expr instanceof Type [var]}).
 */
public record JavaInstanceOfExpression(
    JavaExpression expression,
    JavaType targetType,
    Optional<String> patternVariable,
    SourceSpan span
) implements JavaExpression {
    public JavaInstanceOfExpression {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(patternVariable, "patternVariable must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaInstanceOfExpression of(JavaExpression expr, JavaType targetType, SourceSpan span) {
        return new JavaInstanceOfExpression(expr, targetType, Optional.empty(), span);
    }
}
