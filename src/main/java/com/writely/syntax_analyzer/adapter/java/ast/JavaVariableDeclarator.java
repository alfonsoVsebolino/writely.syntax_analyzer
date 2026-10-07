package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single variable declarator (e.g. {@code x = 10} or {@code arr[] = new int[5]}).
 */
public record JavaVariableDeclarator(
    String name,
    int extraArrayDimensions,
    Optional<JavaExpression> initializer,
    SourceSpan span
) implements JavaAstNode {
    public JavaVariableDeclarator {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(initializer, "initializer must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaVariableDeclarator of(String name, SourceSpan span) {
        return new JavaVariableDeclarator(name, 0, Optional.empty(), span);
    }

    public static JavaVariableDeclarator of(String name, JavaExpression initializer, SourceSpan span) {
        return new JavaVariableDeclarator(name, 0, Optional.ofNullable(initializer), span);
    }
}
