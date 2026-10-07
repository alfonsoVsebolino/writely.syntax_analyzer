package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an object instantiation expression (e.g. {@code new MyClass(a, b)}).
 */
public record JavaObjectCreationExpression(
    Optional<JavaExpression> enclosing,
    JavaType type,
    List<JavaExpression> arguments,
    Optional<List<JavaAstNode>> anonymousClassBody,
    SourceSpan span
) implements JavaExpression {
    public JavaObjectCreationExpression {
        Objects.requireNonNull(enclosing, "enclosing must not be null");
        Objects.requireNonNull(type, "type must not be null");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        Objects.requireNonNull(anonymousClassBody, "anonymousClassBody must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
