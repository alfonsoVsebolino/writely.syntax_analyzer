package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a member field declaration in a class or interface.
 */
public record JavaFieldDeclaration(
    JavaType type,
    List<String> modifiers,
    List<JavaAnnotation> annotations,
    List<JavaVariableDeclarator> variables,
    SourceSpan span
) implements JavaAstNode {
    public JavaFieldDeclaration {
        Objects.requireNonNull(type, "type must not be null");
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        variables = variables == null ? List.of() : List.copyOf(variables);
        Objects.requireNonNull(span, "span must not be null");
    }
}
