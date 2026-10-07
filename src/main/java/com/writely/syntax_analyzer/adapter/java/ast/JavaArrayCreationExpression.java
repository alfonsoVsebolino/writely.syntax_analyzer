package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an array instantiation expression (e.g. {@code new int[10]} or {@code new int[] {1, 2}}).
 */
public record JavaArrayCreationExpression(
    JavaType elementType,
    List<JavaExpression> dimensionExpressions,
    int emptyDimensions,
    Optional<List<JavaExpression>> initializer,
    SourceSpan span
) implements JavaExpression {
    public JavaArrayCreationExpression {
        Objects.requireNonNull(elementType, "elementType must not be null");
        dimensionExpressions = dimensionExpressions == null ? List.of() : List.copyOf(dimensionExpressions);
        Objects.requireNonNull(initializer, "initializer must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
