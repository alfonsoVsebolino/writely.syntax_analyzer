package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a {@code switch} expression evaluating to a value.
 */
public record JavaSwitchExpression(
    JavaExpression selector,
    List<JavaSwitchEntry> entries,
    SourceSpan span
) implements JavaExpression {
    public JavaSwitchExpression {
        Objects.requireNonNull(selector, "selector must not be null");
        entries = entries == null ? List.of() : List.copyOf(entries);
        Objects.requireNonNull(span, "span must not be null");
    }
}
