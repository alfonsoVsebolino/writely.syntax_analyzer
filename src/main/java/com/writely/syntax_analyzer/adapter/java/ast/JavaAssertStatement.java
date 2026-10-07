package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents an {@code assert} statement with an optional detail expression.
 */
public record JavaAssertStatement(
    JavaExpression condition,
    Optional<JavaExpression> detail,
    SourceSpan span
) implements JavaStatement {
    public JavaAssertStatement {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(detail, "detail must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
