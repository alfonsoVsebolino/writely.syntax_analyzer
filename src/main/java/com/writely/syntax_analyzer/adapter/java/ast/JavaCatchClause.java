package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a single catch block within a try statement.
 */
public record JavaCatchClause(
    JavaParameter parameter,
    JavaBlock body,
    SourceSpan span
) implements JavaAstNode {
    public JavaCatchClause {
        Objects.requireNonNull(parameter, "parameter must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
