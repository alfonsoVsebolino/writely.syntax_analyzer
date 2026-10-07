package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a method call expression (e.g. {@code target.method(arg1, arg2)} or {@code print(x)}).
 */
public record JavaMethodInvocationExpression(
    Optional<JavaExpression> target,
    String methodName,
    List<JavaType> typeArguments,
    List<JavaExpression> arguments,
    SourceSpan span
) implements JavaExpression {
    public JavaMethodInvocationExpression {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(methodName, "methodName must not be null");
        typeArguments = typeArguments == null ? List.of() : List.copyOf(typeArguments);
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        Objects.requireNonNull(span, "span must not be null");
    }
}
