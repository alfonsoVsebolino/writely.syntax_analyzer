package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents an array index dereference expression ({@code array[index]}).
 */
public record JavaArrayAccessExpression(
    JavaExpression array,
    JavaExpression index,
    SourceSpan span
) implements JavaExpression {
    public JavaArrayAccessExpression {
        Objects.requireNonNull(array, "array must not be null");
        Objects.requireNonNull(index, "index must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
