package com.writely.syntax_analyzer.core.tokenization;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JavaTokenizer Tests")
class JavaTokenizerTest {

    private final JavaTokenizer tokenizer = new JavaTokenizer();

    @Test
    @DisplayName("Tokenizes Java keywords including modern keywords")
    void testKeywords() {
        String code = "public static void class record sealed permits non-sealed var yield";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(10, tokens.size());
        for (Token t : tokens) {
            assertEquals(TokenType.KEYWORD, t.tokenType());
            assertEquals("KEYWORD", t.category());
        }
        assertEquals("public", tokens.get(0).lexeme());
        assertEquals("static", tokens.get(1).lexeme());
        assertEquals("void", tokens.get(2).lexeme());
        assertEquals("class", tokens.get(3).lexeme());
    }

    @Test
    @DisplayName("Distinguishes keywords from identifiers and literals")
    void testKeywordsVsIdentifiers() {
        String code = "abstract boolean int myVar main";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(5, tokens.size());
        assertEquals(TokenType.KEYWORD, tokens.get(0).tokenType());
        assertEquals("abstract", tokens.get(0).lexeme());

        assertEquals(TokenType.KEYWORD, tokens.get(1).tokenType());
        assertEquals("boolean", tokens.get(1).lexeme());

        assertEquals(TokenType.KEYWORD, tokens.get(2).tokenType());
        assertEquals("int", tokens.get(2).lexeme());

        assertEquals(TokenType.IDENTIFIER, tokens.get(3).tokenType());
        assertEquals("myVar", tokens.get(3).lexeme());

        assertEquals(TokenType.IDENTIFIER, tokens.get(4).tokenType());
        assertEquals("main", tokens.get(4).lexeme());
    }

    @Test
    @DisplayName("Tokenizes contextual keyword non-sealed vs non - sealed subtraction")
    void testNonSealedKeyword() {
        String code = "non-sealed class Foo";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(3, tokens.size());
        assertEquals(TokenType.KEYWORD, tokens.get(0).tokenType());
        assertEquals("non-sealed", tokens.get(0).lexeme());
        assertEquals(TokenType.KEYWORD, tokens.get(1).tokenType());
        assertEquals("class", tokens.get(1).lexeme());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).tokenType());
        assertEquals("Foo", tokens.get(2).lexeme());

        // Subtraction expression non - sealed
        String subtraction = "non - sealed";
        List<Token> subTokens = tokenizer.tokenize(SourcePayload.snippet(subtraction, Language.JAVA));
        assertEquals(3, subTokens.size());
        assertEquals(TokenType.IDENTIFIER, subTokens.get(0).tokenType());
        assertEquals("non", subTokens.get(0).lexeme());
        assertEquals(TokenType.OPERATOR, subTokens.get(1).tokenType());
        assertEquals("-", subTokens.get(1).lexeme());
        assertEquals(TokenType.KEYWORD, subTokens.get(2).tokenType());
        assertEquals("sealed", subTokens.get(2).lexeme());
    }

    @Test
    @DisplayName("Tokenizes boolean and null literals with appropriate categories")
    void testBooleanAndNullLiterals() {
        String code = "true false null";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(3, tokens.size());
        assertEquals(TokenType.KEYWORD, tokens.get(0).tokenType());
        assertEquals("BOOLEAN_LITERAL", tokens.get(0).category());
        assertEquals("true", tokens.get(0).lexeme());

        assertEquals(TokenType.KEYWORD, tokens.get(1).tokenType());
        assertEquals("BOOLEAN_LITERAL", tokens.get(1).category());
        assertEquals("false", tokens.get(1).lexeme());

        assertEquals(TokenType.KEYWORD, tokens.get(2).tokenType());
        assertEquals("NULL_LITERAL", tokens.get(2).category());
        assertEquals("null", tokens.get(2).lexeme());
    }

    @Test
    @DisplayName("Tokenizes operators with greedy matching and delimiters")
    void testOperatorsAndDelimiters() {
        String code = "x >>>= 2; a + b == c && d -> e :: f ? g : h; ...";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals("x", tokens.get(0).lexeme());
        assertEquals(">>>=", tokens.get(1).lexeme());
        assertEquals(TokenType.OPERATOR, tokens.get(1).tokenType());

        assertEquals("2", tokens.get(2).lexeme());
        assertEquals(";", tokens.get(3).lexeme());
        assertEquals(TokenType.DELIMITER, tokens.get(3).tokenType());

        assertEquals("a", tokens.get(4).lexeme());
        assertEquals("+", tokens.get(5).lexeme());
        assertEquals(TokenType.OPERATOR, tokens.get(5).tokenType());

        assertEquals("b", tokens.get(6).lexeme());
        assertEquals("==", tokens.get(7).lexeme());
        assertEquals(TokenType.OPERATOR, tokens.get(7).tokenType());

        assertEquals("c", tokens.get(8).lexeme());
        assertEquals("&&", tokens.get(9).lexeme());
        assertEquals(TokenType.OPERATOR, tokens.get(9).tokenType());

        assertEquals("d", tokens.get(10).lexeme());
        assertEquals("->", tokens.get(11).lexeme());
        assertEquals(TokenType.OPERATOR, tokens.get(11).tokenType());

        assertEquals("e", tokens.get(12).lexeme());
        assertEquals("::", tokens.get(13).lexeme());
        assertEquals(TokenType.OPERATOR, tokens.get(13).tokenType());

        // Delimiter ...
        Token varargs = tokens.get(tokens.size() - 1);
        assertEquals("...", varargs.lexeme());
        assertEquals(TokenType.DELIMITER, varargs.tokenType());
    }

    @Test
    @DisplayName("Tokenizes integer, float, hex, binary, and scientific numbers")
    void testNumericLiterals() {
        String code = "42 1_000_000L 0x1A_FF 0b1010_0111 3.14 3.14f 1e-5 .5 077";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(9, tokens.size());
        for (Token t : tokens) {
            assertEquals(TokenType.LITERAL_NUMBER, t.tokenType());
            assertTrue(t.tokenType().isLiteral());
        }
        assertEquals("42", tokens.get(0).lexeme());
        assertEquals("1_000_000L", tokens.get(1).lexeme());
        assertEquals("0x1A_FF", tokens.get(2).lexeme());
        assertEquals("0b1010_0111", tokens.get(3).lexeme());
        assertEquals("3.14", tokens.get(4).lexeme());
        assertEquals("3.14f", tokens.get(5).lexeme());
        assertEquals("1e-5", tokens.get(6).lexeme());
        assertEquals(".5", tokens.get(7).lexeme());
        assertEquals("077", tokens.get(8).lexeme());
    }

    @Test
    @DisplayName("Tokenizes strings and characters with escapes and text blocks")
    void testStringAndCharLiterals() {
        String code = "\"hello world\" 'a' '\\n' \"\"\"multiline\ntext block\"\"\"";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(4, tokens.size());
        assertEquals(TokenType.LITERAL_STRING, tokens.get(0).tokenType());
        assertEquals("\"hello world\"", tokens.get(0).lexeme());

        assertEquals(TokenType.LITERAL_CHAR, tokens.get(1).tokenType());
        assertEquals("'a'", tokens.get(1).lexeme());

        assertEquals(TokenType.LITERAL_CHAR, tokens.get(2).tokenType());
        assertEquals("'\\n'", tokens.get(2).lexeme());

        assertEquals(TokenType.LITERAL_STRING, tokens.get(3).tokenType());
        assertEquals("\"\"\"multiline\ntext block\"\"\"", tokens.get(3).lexeme());
    }

    @Test
    @DisplayName("Preserves unterminated string and char literals with exact spans")
    void testUnterminatedLiterals() {
        String code = "\"unterminated string\n'u";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(2, tokens.size());
        Token unterminatedStr = tokens.get(0);
        assertEquals(TokenType.LITERAL_STRING, unterminatedStr.tokenType());
        assertEquals("\"unterminated string", unterminatedStr.lexeme());
        assertEquals(1, unterminatedStr.startLocation().line());
        assertEquals(1, unterminatedStr.startLocation().column());

        Token unterminatedChar = tokens.get(1);
        assertEquals(TokenType.LITERAL_CHAR, unterminatedChar.tokenType());
        assertEquals("'u", unterminatedChar.lexeme());
        assertEquals(2, unterminatedChar.startLocation().line());
        assertEquals(1, unterminatedChar.startLocation().column());
    }

    @Test
    @DisplayName("Comments classified as trivia; isolated from code analysis")
    void testCommentsAndTriviaIsolation() {
        String code = "// x = 10; { [ ] }\nint y; /* int z = 20; */";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(5, tokens.size());

        Token lineComment = tokens.get(0);
        assertEquals(TokenType.COMMENT, lineComment.tokenType());
        assertTrue(lineComment.tokenType().isTrivia());
        assertEquals("// x = 10; { [ ] }", lineComment.lexeme());

        Token kw = tokens.get(1);
        assertEquals(TokenType.KEYWORD, kw.tokenType());
        assertEquals("int", kw.lexeme());
        assertEquals(2, kw.startLocation().line());
        assertEquals(1, kw.startLocation().column());

        Token id = tokens.get(2);
        assertEquals(TokenType.IDENTIFIER, id.tokenType());
        assertEquals("y", id.lexeme());

        Token semi = tokens.get(3);
        assertEquals(TokenType.DELIMITER, semi.tokenType());
        assertEquals(";", semi.lexeme());

        Token blockComment = tokens.get(4);
        assertEquals(TokenType.COMMENT, blockComment.tokenType());
        assertTrue(blockComment.tokenType().isTrivia());
        assertEquals("/* int z = 20; */", blockComment.lexeme());

        // Unterminated block comment
        List<Token> unterminatedBlock = tokenizer.tokenize(SourcePayload.snippet("/* unclosed", Language.JAVA));
        assertEquals(1, unterminatedBlock.size());
        assertEquals(TokenType.COMMENT, unterminatedBlock.get(0).tokenType());
        assertEquals("/* unclosed", unterminatedBlock.get(0).lexeme());

        // Hex floating point literal with p exponent
        List<Token> hexFloats = tokenizer.tokenize(SourcePayload.snippet("0x1.0p-3 0x1.0p+3 0x1p4", Language.JAVA));
        assertEquals(3, hexFloats.size());
        for (Token t : hexFloats) {
            assertEquals(TokenType.LITERAL_NUMBER, t.tokenType());
        }

        // Annotation @ delimiter
        List<Token> annotationTokens = tokenizer.tokenize(SourcePayload.snippet("@Override", Language.JAVA));
        assertEquals(2, annotationTokens.size());
        assertEquals(TokenType.DELIMITER, annotationTokens.get(0).tokenType());
        assertEquals("@", annotationTokens.get(0).lexeme());
    }

    @Test
    @DisplayName("Exact coordinate tracking for comments and statements across lines")
    void testCoordinatesAcrossLines() {
        String code = "// header\nint val = 5;";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(6, tokens.size());

        Token comment = tokens.get(0);
        assertEquals(SourceLocation.of(1, 1, 0), comment.startLocation());
        assertEquals(SourceLocation.of(1, 10, 9), comment.endLocation());
        assertEquals(9, comment.span().length());

        Token kw = tokens.get(1);
        assertEquals("int", kw.lexeme());
        assertEquals(SourceLocation.of(2, 1, 10), kw.startLocation());
        assertEquals(SourceLocation.of(2, 4, 13), kw.endLocation());
        assertEquals(3, kw.span().length());

        Token id = tokens.get(2);
        assertEquals("val", id.lexeme());
        assertEquals(SourceLocation.of(2, 5, 14), id.startLocation());
        assertEquals(SourceLocation.of(2, 8, 17), id.endLocation());
        assertEquals(3, id.span().length());

        Token op = tokens.get(3);
        assertEquals("=", op.lexeme());
        assertEquals(SourceLocation.of(2, 9, 18), op.startLocation());
        assertEquals(SourceLocation.of(2, 10, 19), op.endLocation());

        Token num = tokens.get(4);
        assertEquals("5", num.lexeme());
        assertEquals(SourceLocation.of(2, 11, 20), num.startLocation());
        assertEquals(SourceLocation.of(2, 12, 21), num.endLocation());

        Token semi = tokens.get(5);
        assertEquals(";", semi.lexeme());
        assertEquals(SourceLocation.of(2, 12, 21), semi.startLocation());
        assertEquals(SourceLocation.of(2, 13, 22), semi.endLocation());
    }

    @Test
    @DisplayName("Tabs advance column by 1 char offset per ADR 0004")
    void testTabsInJavaSource() {
        String code = "\tint x;";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(3, tokens.size());
        Token kw = tokens.get(0);
        assertEquals("int", kw.lexeme());
        assertEquals(1, kw.startLocation().line());
        assertEquals(2, kw.startLocation().column()); // after 1 tab
        assertEquals(1, kw.startLocation().charOffset());
    }

    @Test
    @DisplayName("Preserves unknown characters as UNKNOWN tokens")
    void testUnknownCharacters() {
        String code = "int # $ `";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(4, tokens.size());
        assertEquals(TokenType.KEYWORD, tokens.get(0).tokenType());
        assertEquals(TokenType.UNKNOWN, tokens.get(1).tokenType());
        assertEquals("#", tokens.get(1).lexeme());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).tokenType()); // $ is valid java identifier start!
        assertEquals("$", tokens.get(2).lexeme());
        assertEquals(TokenType.UNKNOWN, tokens.get(3).tokenType());
        assertEquals("`", tokens.get(3).lexeme());
    }

    @Test
    @DisplayName("Preserves whitespace when configured")
    void testPreserveWhitespace() {
        JavaTokenizer wsTokenizer = new JavaTokenizer(true);
        String code = "int x;";
        List<Token> tokens = wsTokenizer.tokenize(SourcePayload.snippet(code, Language.JAVA));

        assertEquals(4, tokens.size());
        assertEquals(TokenType.KEYWORD, tokens.get(0).tokenType());
        assertEquals(TokenType.WHITESPACE, tokens.get(1).tokenType());
        assertEquals(" ", tokens.get(1).lexeme());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).tokenType());
        assertEquals(TokenType.DELIMITER, tokens.get(3).tokenType());
    }

    @Test
    @DisplayName("Handles empty source payload")
    void testEmptySource() {
        List<Token> tokens = tokenizer.tokenize(SourcePayload.snippet("", Language.JAVA));
        assertTrue(tokens.isEmpty());
    }

    @Test
    @DisplayName("Rejects null payload or non-Java language")
    void testInvariants() {
        assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null));
        assertThrows(IllegalArgumentException.class, () ->
            tokenizer.tokenize(SourcePayload.snippet("x = 1", Language.PYTHON))
        );
    }
}
