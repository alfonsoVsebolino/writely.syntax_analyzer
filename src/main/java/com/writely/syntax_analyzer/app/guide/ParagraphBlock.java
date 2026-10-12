package com.writely.syntax_analyzer.app.guide;

import java.util.Objects;

/**
 * Immutable block representing a paragraph of text.
 */
public record ParagraphBlock(String text) implements GuideBlock {
    public ParagraphBlock {
        Objects.requireNonNull(text, "text must not be null");
    }
}
