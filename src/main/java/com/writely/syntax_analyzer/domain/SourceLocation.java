package com.writely.syntax_analyzer.domain;

import java.util.Objects;

/**
 * Represents a discrete coordinate in source text.
 * Line and column are 1-based; charOffset is 0-based.
 */
public record SourceLocation(int line, int column, int charOffset) implements Comparable<SourceLocation> {

    public SourceLocation {
        if (line < 1) {
            throw new IllegalArgumentException("Line must be >= 1, but was: " + line);
        }
        if (column < 1) {
            throw new IllegalArgumentException("Column must be >= 1, but was: " + column);
        }
        if (charOffset < 0) {
            throw new IllegalArgumentException("charOffset must be >= 0, but was: " + charOffset);
        }
    }

    public static SourceLocation of(int line, int column, int charOffset) {
        return new SourceLocation(line, column, charOffset);
    }

    public static SourceLocation of(int line, int column) {
        return new SourceLocation(line, column, 0);
    }

    public static SourceLocation start() {
        return new SourceLocation(1, 1, 0);
    }

    @Override
    public int compareTo(SourceLocation other) {
        Objects.requireNonNull(other, "other location must not be null");
        int cmp = Integer.compare(this.line, other.line);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.column, other.column);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(this.charOffset, other.charOffset);
    }

    @Override
    public String toString() {
        return line + ":" + column + " (offset: " + charOffset + ")";
    }
}
