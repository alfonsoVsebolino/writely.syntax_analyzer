package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a Java annotation (e.g. {@code @Override}, {@code @SuppressWarnings("unchecked")}).
 */
public record JavaAnnotation(
    String name,
    Optional<String> arguments,
    SourceSpan span
) implements JavaAstNode {
    public JavaAnnotation {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaAnnotation of(String name, SourceSpan span) {
        return new JavaAnnotation(name, Optional.empty(), span);
    }

    public static JavaAnnotation of(String name, String arguments, SourceSpan span) {
        return new JavaAnnotation(name, Optional.ofNullable(arguments), span);
    }
}
