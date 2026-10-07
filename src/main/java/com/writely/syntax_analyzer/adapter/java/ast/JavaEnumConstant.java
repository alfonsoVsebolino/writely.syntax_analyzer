package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents an enum constant declaration.
 */
public record JavaEnumConstant(
    String name,
    List<JavaAnnotation> annotations,
    List<JavaExpression> arguments,
    List<JavaAstNode> classBody,
    SourceSpan span
) implements JavaAstNode {
    public JavaEnumConstant {
        Objects.requireNonNull(name, "name must not be null");
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        classBody = classBody == null ? List.of() : List.copyOf(classBody);
        Objects.requireNonNull(span, "span must not be null");
    }
}
