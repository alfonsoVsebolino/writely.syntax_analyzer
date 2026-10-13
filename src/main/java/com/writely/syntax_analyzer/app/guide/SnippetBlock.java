package com.writely.syntax_analyzer.app.guide;

import java.util.Objects;

/**
 * Immutable block representing a code snippet.
 */
public record SnippetBlock(String code) implements GuideBlock {
    public SnippetBlock {
        Objects.requireNonNull(code, "code must not be null");
    }
}
