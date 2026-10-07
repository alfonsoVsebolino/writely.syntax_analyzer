package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents an import declaration (single-type, type-on-demand, static).
 */
public record JavaImportDeclaration(
    String importName,
    boolean isStatic,
    boolean isWildcard,
    SourceSpan span
) implements JavaAstNode {
    public JavaImportDeclaration {
        Objects.requireNonNull(importName, "importName must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
