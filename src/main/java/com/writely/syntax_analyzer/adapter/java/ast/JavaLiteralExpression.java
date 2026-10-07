package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a literal constant expression (integer, float, string, char, boolean, null).
 */
public record JavaLiteralExpression(
    String rawText,
    String literalKind,
    SourceSpan span
) implements JavaExpression {
    public JavaLiteralExpression {
        Objects.requireNonNull(rawText, "rawText must not be null");
        Objects.requireNonNull(literalKind, "literalKind must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
