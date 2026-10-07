package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a type reference in Java (primitive, class, generic, array).
 */
public record JavaType(
    String name,
    List<JavaType> typeArguments,
    int arrayDimensions,
    SourceSpan span
) implements JavaAstNode {
    public JavaType {
        Objects.requireNonNull(name, "name must not be null");
        typeArguments = typeArguments == null ? List.of() : List.copyOf(typeArguments);
        Objects.requireNonNull(span, "span must not be null");
    }

    public static JavaType of(String name, SourceSpan span) {
        return new JavaType(name, List.of(), 0, span);
    }

    public static JavaType of(String name, List<JavaType> typeArgs, SourceSpan span) {
        return new JavaType(name, typeArgs, 0, span);
    }

    public static JavaType array(JavaType base, int dims, SourceSpan span) {
        return new JavaType(base.name(), base.typeArguments(), base.arrayDimensions() + dims, span);
    }

    public String fullTypeName() {
        StringBuilder sb = new StringBuilder(name);
        if (!typeArguments.isEmpty()) {
            sb.append("<");
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(typeArguments.get(i).fullTypeName());
            }
            sb.append(">");
        }
        sb.append("[]".repeat(Math.max(0, arrayDimensions)));
        return sb.toString();
    }
}
