package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents an instance or static class initializer block.
 */
public record JavaInitializerBlock(
    boolean isStatic,
    JavaBlock body,
    SourceSpan span
) implements JavaAstNode {
    public JavaInitializerBlock {
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
