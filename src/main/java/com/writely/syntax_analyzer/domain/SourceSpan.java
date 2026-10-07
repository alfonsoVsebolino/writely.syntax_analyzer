package com.writely.syntax_analyzer.domain;

import java.util.Objects;

/**
 * Represents a contiguous range in source text bounded by a start and end location.
 */
public record SourceSpan(SourceLocation start, SourceLocation end) {

    public SourceSpan {
        Objects.requireNonNull(start, "start location must not be null");
        Objects.requireNonNull(end, "end location must not be null");
        if (start.charOffset() > end.charOffset()) {
            throw new IllegalArgumentException(
                "start.charOffset (" + start.charOffset() + ") must be <= end.charOffset (" + end.charOffset() + ")"
            );
        }
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException(
                "start location (" + start + ") must not be after end location (" + end + ")"
            );
        }
    }

    public static SourceSpan of(SourceLocation start, SourceLocation end) {
        return new SourceSpan(start, end);
    }

    public static SourceSpan point(SourceLocation location) {
        return new SourceSpan(location, location);
    }

    public static SourceSpan of(
        int startLine,
        int startColumn,
        int startOffset,
        int endLine,
        int endColumn,
        int endOffset
    ) {
        return new SourceSpan(
            SourceLocation.of(startLine, startColumn, startOffset),
            SourceLocation.of(endLine, endColumn, endOffset)
        );
    }

    /**
     * Number of characters spanned (end charOffset - start charOffset).
     */
    public int length() {
        return end.charOffset() - start.charOffset();
    }

    /**
     * Returns true if start and end represent the exact same location.
     */
    public boolean isEmpty() {
        return start.equals(end);
    }

    /**
     * Checks whether the given location falls inclusively within this span.
     */
    public boolean contains(SourceLocation location) {
        Objects.requireNonNull(location, "location must not be null");
        return start.compareTo(location) <= 0 && location.compareTo(end) <= 0;
    }

    /**
     * Checks whether the given character offset falls inclusively within this span.
     */
    public boolean contains(int charOffset) {
        return charOffset >= start.charOffset() && charOffset <= end.charOffset();
    }
}
