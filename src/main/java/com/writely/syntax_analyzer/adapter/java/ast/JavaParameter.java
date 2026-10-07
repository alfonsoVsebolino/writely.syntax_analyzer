package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a formal parameter in a method, constructor, lambda, or record component.
 */
public record JavaParameter(
    JavaType type,
    String name,
    List<JavaAnnotation> annotations,
    List<String> modifiers,
    boolean isVarargs,
    SourceSpan span
) implements JavaAstNode {
    public JavaParameter {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(name, "name must not be null");
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaParameter of(JavaType type, String name, SourceSpan span) {
        return new JavaParameter(type, name, List.of(), List.of(), false, span);
    }
}
