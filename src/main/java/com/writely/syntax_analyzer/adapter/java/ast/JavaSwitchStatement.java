package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;

/**
 * Represents a {@code switch} control structure statement.
 */
public record JavaSwitchStatement(
    JavaExpression selector,
    List<JavaSwitchEntry> entries,
    SourceSpan span
) implements JavaStatement {
    public JavaSwitchStatement {
        Objects.requireNonNull(selector, "selector must not be null");
        entries = entries == null ? List.of() : List.copyOf(entries);
        Objects.requireNonNull(span, "span must not be null");
    }
}
