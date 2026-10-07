package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single switch case entry (classic colon or rule arrow).
 */
public record JavaSwitchEntry(
    List<JavaExpression> labels,
    boolean isDefault,
    boolean isRule,
    List<JavaStatement> statements,
    SourceSpan span
) implements JavaAstNode {
    public JavaSwitchEntry {
        labels = labels == null ? List.of() : List.copyOf(labels);
        statements = statements == null ? List.of() : List.copyOf(statements);
        Objects.requireNonNull(span, "span must not be null");
    }
}
