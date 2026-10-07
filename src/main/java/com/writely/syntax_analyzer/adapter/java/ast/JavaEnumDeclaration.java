package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a Java enum declaration.
 */
public record JavaEnumDeclaration(
    String name,
    List<String> modifiers,
    List<JavaAnnotation> annotations,
    List<JavaType> interfaces,
    List<JavaEnumConstant> constants,
    List<JavaAstNode> members,
    SourceSpan span
) implements JavaAstNode {
    public JavaEnumDeclaration {
        Objects.requireNonNull(name, "name must not be null");
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
        constants = constants == null ? List.of() : List.copyOf(constants);
        members = members == null ? List.of() : List.copyOf(members);
        Objects.requireNonNull(span, "span must not be null");
    }
}
