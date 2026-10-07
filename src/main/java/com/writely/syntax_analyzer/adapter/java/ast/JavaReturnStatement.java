package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code return} statement with an optional return expression.
 */
public record JavaReturnStatement(
    Optional<JavaExpression> value,
    SourceSpan span
) implements JavaStatement {
    public JavaReturnStatement {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaReturnStatement of(SourceSpan span) {
        return new JavaReturnStatement(Optional.empty(), span);
    }

    public static JavaReturnStatement of(JavaExpression value, SourceSpan span) {
        return new JavaReturnStatement(Optional.ofNullable(value), span);
    }
}
