package com.writely.syntax_analyzer.app.guide;

import java.util.List;
import java.util.Objects;

/**
 * Immutable block representing an ordered list of bullet items.
 */
public record BulletListBlock(List<String> items) implements GuideBlock {
    public BulletListBlock {
        Objects.requireNonNull(items, "items must not be null");
        items = List.copyOf(items);
    }

    public BulletListBlock(String... items) {
        this(List.of(items));
    }
}
