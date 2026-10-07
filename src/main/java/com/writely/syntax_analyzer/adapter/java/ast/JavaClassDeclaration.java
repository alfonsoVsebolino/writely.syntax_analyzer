package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a Java class declaration.
 */
public record JavaClassDeclaration(
    String name,
    List<String> modifiers,
    List<JavaAnnotation> annotations,
    List<JavaTypeParameter> typeParameters,
    Optional<JavaType> superclass,
    List<JavaType> interfaces,
    List<JavaType> permittedSubclasses,
    List<JavaAstNode> members,
    SourceSpan span
) implements JavaAstNode {
    public JavaClassDeclaration {
        Objects.requireNonNull(name, "name must not be null");
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        typeParameters = typeParameters == null ? List.of() : List.copyOf(typeParameters);
        Objects.requireNonNull(superclass, "superclass must not be null");
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
        permittedSubclasses = permittedSubclasses == null ? List.of() : List.copyOf(permittedSubclasses);
        members = members == null ? List.of() : List.copyOf(members);
        Objects.requireNonNull(span, "span must not be null");
    }
}
