package com.writely.syntax_analyzer.app.guide;

import java.util.List;
import java.util.Objects;

/**
 * Immutable guide section with a title and an ordered list of content blocks.
 */
public record GuideSection(String title, List<GuideBlock> blocks) {
    public GuideSection {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(blocks, "blocks must not be null");
        blocks = List.copyOf(blocks);
    }

    public GuideSection(String title, GuideBlock... blocks) {
        this(title, List.of(blocks));
    }
}
