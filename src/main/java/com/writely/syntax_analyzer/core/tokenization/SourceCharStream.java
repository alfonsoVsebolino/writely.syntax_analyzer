package com.writely.syntax_analyzer.core.tokenization;

import com.writely.syntax_analyzer.domain.SourceLocation;

import java.util.Objects;

/**
 * Coordinate tracking character stream that tracks 0-based character offsets,
 * 1-based lines, and 1-based columns according to ADR 0004.
 */
public final class SourceCharStream {

    private final String source;
    private int charOffset;
    private int line;
    private int column;

    /**
     * Snapshot record for saving and restoring stream coordinates during lookahead.
     */
    public record Position(int charOffset, int line, int column) {}

    public SourceCharStream(String source) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.charOffset = 0;
        this.line = 1;
        this.column = 1;
    }

    /**
     * Current 0-based character offset.
     */
    public int charOffset() {
        return charOffset;
    }

    /**
     * Current 1-based line number.
     */
    public int line() {
        return line;
    }

    /**
     * Current 1-based column number.
     */
    public int column() {
        return column;
    }

    /**
     * Current location coordinates.
     */
    public SourceLocation location() {
        return SourceLocation.of(line, column, charOffset);
    }

    /**
     * Returns true if there are more characters to consume.
     */
    public boolean hasMore() {
        return charOffset < source.length();
    }

    /**
     * Returns true if the end of the stream has been reached.
     */
    public boolean isEof() {
        return charOffset >= source.length();
    }

    /**
     * Peeks at the current character without advancing the stream.
     * Returns '\0' if at EOF.
     */
    public char peek() {
        return hasMore() ? source.charAt(charOffset) : '\0';
    }

    /**
     * Peeks at character at {@code offset} positions ahead of current offset without advancing.
     *
     * @param offset relative lookahead offset (0 is current char, 1 is next, etc.)
     * @return character at lookahead position, or '\0' if out of bounds
     */
    public char peek(int offset) {
        int target = charOffset + offset;
        return (target >= 0 && target < source.length()) ? source.charAt(target) : '\0';
    }

    /**
     * Checks whether remaining text begins with specified prefix.
     */
    public boolean startsWith(String prefix) {
        if (prefix == null) {
            return false;
        }
        int len = prefix.length();
        if (charOffset + len > source.length()) {
            return false;
        }
        return source.startsWith(prefix, charOffset);
    }

    /**
     * Consumes and returns the current character, updating charOffset, line, and column.
     * Tabs advance the column by 1 char offset per ADR 0004.
     */
    public char consume() {
        if (!hasMore()) {
            return '\0';
        }
        char current = source.charAt(charOffset++);
        if (current == '\r') {
            if (hasMore() && source.charAt(charOffset) == '\n') {
                // CRLF: advance column for '\r'; newline transition occurs on '\n'
                column++;
            } else {
                // Standalone CR newline
                line++;
                column = 1;
            }
        } else if (current == '\n') {
            line++;
            column = 1;
        } else {
            // Standard characters and tabs advance column by 1
            column++;
        }
        return current;
    }

    /**
     * Advances the stream by one character.
     */
    public void advance() {
        consume();
    }

    /**
     * Advances the stream by {@code count} characters.
     */
    public void advance(int count) {
        for (int i = 0; i < count && hasMore(); i++) {
            consume();
        }
    }

    /**
     * Captures current coordinate state.
     */
    public Position mark() {
        return new Position(charOffset, line, column);
    }

    /**
     * Restores coordinate state to a previously captured mark.
     */
    public void restore(Position pos) {
        Objects.requireNonNull(pos, "pos must not be null");
        this.charOffset = pos.charOffset();
        this.line = pos.line();
        this.column = pos.column();
    }

    /**
     * Returns the full original source string.
     */
    public String sourceText() {
        return source;
    }

    /**
     * Returns total length of original source.
     */
    public int length() {
        return source.length();
    }

    /**
     * Returns a substring of the source between startOffset and endOffset.
     */
    public String substring(int startOffset, int endOffset) {
        return source.substring(startOffset, endOffset);
    }

    /**
     * Returns remaining unconsumed source text.
     */
    public String remainder() {
        return source.substring(charOffset);
    }
}
