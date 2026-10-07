package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents an assignment expression (e.g. {@code x = 10}, {@code y += 5}).
 */
public record JavaAssignmentExpression(
    JavaExpression target,
    String operator,
    JavaExpression value,
    SourceSpan span
) implements JavaExpression {
    public JavaAssignmentExpression {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
