package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a Java interface declaration.
 */
public record JavaInterfaceDeclaration(
    String name,
    List<String> modifiers,
    List<JavaAnnotation> annotations,
    List<JavaTypeParameter> typeParameters,
    List<JavaType> extendedInterfaces,
    List<JavaType> permittedSubclasses,
    List<JavaAstNode> members,
    SourceSpan span
) implements JavaAstNode {
    public JavaInterfaceDeclaration {
        Objects.requireNonNull(name, "name must not be null");
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        typeParameters = typeParameters == null ? List.of() : List.copyOf(typeParameters);
        extendedInterfaces = extendedInterfaces == null ? List.of() : List.copyOf(extendedInterfaces);
        permittedSubclasses = permittedSubclasses == null ? List.of() : List.copyOf(permittedSubclasses);
        members = members == null ? List.of() : List.copyOf(members);
        Objects.requireNonNull(span, "span must not be null");
    }
}
