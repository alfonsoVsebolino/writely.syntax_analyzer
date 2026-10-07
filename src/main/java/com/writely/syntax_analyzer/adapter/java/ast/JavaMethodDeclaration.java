package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a method declaration in a class, record, or interface.
 */
public record JavaMethodDeclaration(
    String name,
    JavaType returnType,
    List<String> modifiers,
    List<JavaAnnotation> annotations,
    List<JavaTypeParameter> typeParameters,
    List<JavaParameter> parameters,
    List<JavaType> thrownExceptions,
    Optional<JavaBlock> body,
    SourceSpan span
) implements JavaAstNode {
    public JavaMethodDeclaration {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(returnType, "returnType must not be null");
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        typeParameters = typeParameters == null ? List.of() : List.copyOf(typeParameters);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        thrownExceptions = thrownExceptions == null ? List.of() : List.copyOf(thrownExceptions);
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
