package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a package declaration in a Java compilation unit.
 */
public record JavaPackageDeclaration(
    String packageName,
    List<JavaAnnotation> annotations,
    SourceSpan span
) implements JavaAstNode {
    public JavaPackageDeclaration {
        Objects.requireNonNull(packageName, "packageName must not be null");
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaPackageDeclaration of(String packageName, SourceSpan span) {
        return new JavaPackageDeclaration(packageName, List.of(), span);
    }
}
