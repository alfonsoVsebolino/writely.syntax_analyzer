package com.writely.syntax_analyzer.core.tokenization;

import com.writely.syntax_analyzer.domain.SourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SourceCharStream Tests")
class SourceCharStreamTest {

    @Test
    @DisplayName("Tracks 1-based line, 1-based column, and 0-based charOffset")
    void testBasicPositionTracking() {
        SourceCharStream stream = new SourceCharStream("ab\ncd");

        assertEquals(0, stream.charOffset());
        assertEquals(1, stream.line());
        assertEquals(1, stream.column());
        assertEquals(SourceLocation.of(1, 1, 0), stream.location());
        assertEquals('a', stream.peek());
        assertEquals('b', stream.peek(1));
        assertTrue(stream.startsWith("ab"));
        assertFalse(stream.isEof());
        assertTrue(stream.hasMore());

        assertEquals('a', stream.consume());
        assertEquals(1, stream.charOffset());
        assertEquals(1, stream.line());
        assertEquals(2, stream.column());

        assertEquals('b', stream.consume());
        assertEquals(2, stream.charOffset());
        assertEquals(1, stream.line());
        assertEquals(3, stream.column());

        assertEquals('\n', stream.consume());
        assertEquals(3, stream.charOffset());
        assertEquals(2, stream.line());
        assertEquals(1, stream.column());

        assertEquals('c', stream.consume());
        assertEquals(4, stream.charOffset());
        assertEquals(2, stream.line());
        assertEquals(2, stream.column());

        assertEquals('d', stream.consume());
        assertEquals(5, stream.charOffset());
        assertEquals(2, stream.line());
        assertEquals(3, stream.column());

        assertTrue(stream.isEof());
        assertFalse(stream.hasMore());
        assertEquals('\0', stream.peek());
        assertEquals('\0', stream.consume());
    }

    @Test
    @DisplayName("Tabs advance column by 1 char offset consistently per ADR 0004")
    void testTabTracking() {
        SourceCharStream stream = new SourceCharStream("\tX");
        assertEquals(1, stream.column());
        assertEquals('\t', stream.consume());
        assertEquals(2, stream.column());
        assertEquals(1, stream.charOffset());
        assertEquals('X', stream.consume());
        assertEquals(3, stream.column());
        assertEquals(2, stream.charOffset());
    }

    @Test
    @DisplayName("Handles CRLF and standalone CR newlines properly")
    void testMixedLineEndings() {
        // CRLF test
        SourceCharStream crlfStream = new SourceCharStream("hi\r\nthere");
        crlfStream.advance(2); // consume "hi"
        assertEquals(1, crlfStream.line());
        assertEquals(3, crlfStream.column());

        assertEquals('\r', crlfStream.consume());
        assertEquals(1, crlfStream.line());
        assertEquals(4, crlfStream.column());

        assertEquals('\n', crlfStream.consume());
        assertEquals(2, crlfStream.line());
        assertEquals(1, crlfStream.column());

        assertEquals('t', crlfStream.consume());
        assertEquals(2, crlfStream.line());
        assertEquals(2, crlfStream.column());

        // Standalone CR test
        SourceCharStream crStream = new SourceCharStream("A\rB");
        assertEquals('A', crStream.consume());
        assertEquals('\r', crStream.consume());
        assertEquals(2, crStream.line());
        assertEquals(1, crStream.column());
        assertEquals('B', crStream.consume());
        assertEquals(2, crStream.line());
        assertEquals(2, crStream.column());
    }

    @Test
    @DisplayName("Mark and restore preserves coordinates")
    void testMarkAndRestore() {
        SourceCharStream stream = new SourceCharStream("hello world");
        stream.advance(5); // at space
        SourceCharStream.Position mark = stream.mark();
        assertEquals(5, mark.charOffset());
        assertEquals(1, mark.line());
        assertEquals(6, mark.column());

        stream.advance(6);
        assertTrue(stream.isEof());

        stream.restore(mark);
        assertEquals(5, stream.charOffset());
        assertEquals(1, stream.line());
        assertEquals(6, stream.column());
        assertEquals(' ', stream.peek());
    }

    @Test
    @DisplayName("Peek lookahead and boundary checks")
    void testPeekLookaheadAndBounds() {
        SourceCharStream stream = new SourceCharStream("abc");
        assertEquals('a', stream.peek(0));
        assertEquals('b', stream.peek(1));
        assertEquals('c', stream.peek(2));
        assertEquals('\0', stream.peek(3));
        assertEquals('\0', stream.peek(-1));

        assertFalse(stream.startsWith(null));
        assertFalse(stream.startsWith("abcdef"));
        assertTrue(stream.startsWith("abc"));

        assertEquals("abc", stream.sourceText());
        assertEquals(3, stream.length());
        assertEquals("bc", stream.substring(1, 3));
        assertEquals("abc", stream.remainder());

        SourceCharStream advanceStream = new SourceCharStream("abcdef");
        advanceStream.advance();
        assertEquals(1, advanceStream.charOffset());
        advanceStream.advance(3);
        assertEquals(4, advanceStream.charOffset());
        assertEquals('e', advanceStream.peek());
    }

    @Test
    @DisplayName("Rejects null source text")
    void testNullSourceRejected() {
        assertThrows(NullPointerException.class, () -> new SourceCharStream(null));
    }
}
