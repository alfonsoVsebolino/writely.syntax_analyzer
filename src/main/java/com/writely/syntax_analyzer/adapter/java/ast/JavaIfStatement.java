package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents an {@code if} (and optional {@code else}) conditional statement.
 */
public record JavaIfStatement(
    JavaExpression condition,
    JavaStatement thenBranch,
    Optional<JavaStatement> elseBranch,
    SourceSpan span
) implements JavaStatement {
    public JavaIfStatement {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(thenBranch, "thenBranch must not be null");
        Objects.requireNonNull(elseBranch, "elseBranch must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
