package com.writely.syntax_analyzer.app.guide;

import java.util.List;
import java.util.Objects;

/**
 * Immutable document representing the complete guide with ordered sections.
 */
public record GuideDocument(List<GuideSection> sections) {
    public GuideDocument {
        Objects.requireNonNull(sections, "sections must not be null");
        sections = List.copyOf(sections);
    }

    public GuideDocument(GuideSection... sections) {
        this(List.of(sections));
    }
}
