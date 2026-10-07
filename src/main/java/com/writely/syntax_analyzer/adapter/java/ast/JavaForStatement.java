package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a standard three-part {@code for} loop.
 */
public record JavaForStatement(
    Optional<JavaAstNode> init,
    Optional<JavaExpression> condition,
    List<JavaExpression> update,
    JavaStatement body,
    SourceSpan span
) implements JavaStatement {
    public JavaForStatement {
        Objects.requireNonNull(init, "init must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
        update = update == null ? List.of() : List.copyOf(update);
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
