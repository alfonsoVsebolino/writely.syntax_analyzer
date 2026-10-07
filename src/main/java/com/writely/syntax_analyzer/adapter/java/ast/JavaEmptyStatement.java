package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents an empty statement consisting solely of a semicolon {@code ;}.
 */
public record JavaEmptyStatement(
    SourceSpan span
) implements JavaStatement {
    public JavaEmptyStatement {
        Objects.requireNonNull(span, "span must not be null");
    }
}
