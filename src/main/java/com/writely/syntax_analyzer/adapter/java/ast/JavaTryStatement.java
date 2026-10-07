package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code try} (with optional resources, catch clauses, and finally block) statement.
 */
public record JavaTryStatement(
    List<JavaAstNode> resources,
    JavaBlock tryBlock,
    List<JavaCatchClause> catchClauses,
    Optional<JavaBlock> finallyBlock,
    SourceSpan span
) implements JavaStatement {
    public JavaTryStatement {
        resources = resources == null ? List.of() : List.copyOf(resources);
        Objects.requireNonNull(tryBlock, "tryBlock must not be null");
        catchClauses = catchClauses == null ? List.of() : List.copyOf(catchClauses);
        Objects.requireNonNull(finallyBlock, "finallyBlock must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
