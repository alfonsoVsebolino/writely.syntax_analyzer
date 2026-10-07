package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a generic type parameter (e.g. {@code T}, {@code E extends Comparable<E>}).
 */
public record JavaTypeParameter(
    String name,
    List<JavaType> bounds,
    SourceSpan span
) implements JavaAstNode {
    public JavaTypeParameter {
        Objects.requireNonNull(name, "name must not be null");
        bounds = bounds == null ? List.of() : List.copyOf(bounds);
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaTypeParameter of(String name, SourceSpan span) {
        return new JavaTypeParameter(name, List.of(), span);
    }
}
