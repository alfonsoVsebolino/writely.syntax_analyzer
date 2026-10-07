package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a Java record declaration.
 */
public record JavaRecordDeclaration(
    String name,
    List<String> modifiers,
    List<JavaAnnotation> annotations,
    List<JavaTypeParameter> typeParameters,
    List<JavaParameter> components,
    List<JavaType> interfaces,
    List<JavaAstNode> members,
    SourceSpan span
) implements JavaAstNode {
    public JavaRecordDeclaration {
        Objects.requireNonNull(name, "name must not be null");
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        typeParameters = typeParameters == null ? List.of() : List.copyOf(typeParameters);
        components = components == null ? List.of() : List.copyOf(components);
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
        members = members == null ? List.of() : List.copyOf(members);
        Objects.requireNonNull(span, "span must not be null");
    }
}
