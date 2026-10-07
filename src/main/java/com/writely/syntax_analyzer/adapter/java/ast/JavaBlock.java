package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a block statement enclosed in curly braces {@code { ... }}.
 */
public record JavaBlock(
    List<JavaStatement> statements,
    SourceSpan span
) implements JavaAstNode, JavaStatement {
    public JavaBlock {
        statements = statements == null ? List.of() : List.copyOf(statements);
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaBlock of(List<JavaStatement> statements, SourceSpan span) {
        return new JavaBlock(statements, span);
    }

    public static JavaBlock empty(SourceSpan span) {
        return new JavaBlock(List.of(), span);
    }
}
