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

@DisplayName("PythonTokenizer Tests")
class PythonTokenizerTest {

    private final PythonTokenizer tokenizer = new PythonTokenizer();

    @Test
    @DisplayName("Tokenizes Python keywords including False, None, True, match, case")
    void testKeywords() {
        String code = "def foo():\n    return False or True is not None\nmatch val:\n    case 1:\n        pass";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("def") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("False") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("True") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("None") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("match") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("case") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("pass") && t.tokenType() == TokenType.KEYWORD));
    }

    @Test
    @DisplayName("Tracks indentation stack emitting INDENT and DEDENT tokens")
    void testIndentationAndDedents() {
        String code = "def bar():\n    x = 1\n    return x";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        // Sequence: def, bar, (, ), :, NEWLINE, INDENT, x, =, 1, NEWLINE, return, x, NEWLINE, DEDENT
        assertEquals("def", tokens.get(0).lexeme());
        assertEquals("bar", tokens.get(1).lexeme());
        assertEquals("(", tokens.get(2).lexeme());
        assertEquals(")", tokens.get(3).lexeme());
        assertEquals(":", tokens.get(4).lexeme());
        assertEquals(TokenType.DELIMITER, tokens.get(4).tokenType());
        assertEquals(TokenType.NEWLINE, tokens.get(5).tokenType());

        Token indent = tokens.get(6);
        assertEquals(TokenType.INDENT, indent.tokenType());
        assertEquals("    ", indent.lexeme());
        assertEquals(SourceLocation.of(2, 1, 11), indent.startLocation());
        assertEquals(SourceLocation.of(2, 5, 15), indent.endLocation());

        assertEquals("x", tokens.get(7).lexeme());
        assertEquals("=", tokens.get(8).lexeme());
        assertEquals("1", tokens.get(9).lexeme());
        assertEquals(TokenType.NEWLINE, tokens.get(10).tokenType());

        assertEquals("return", tokens.get(11).lexeme());
        assertEquals("x", tokens.get(12).lexeme());
        assertEquals(TokenType.NEWLINE, tokens.get(13).tokenType());

        Token dedent = tokens.get(14);
        assertEquals(TokenType.DEDENT, dedent.tokenType());
    }

    @Test
    @DisplayName("Emits multiple DEDENTs when popping multiple indentation levels")
    void testNestedIndentationMultipleDedents() {
        String code = "if a:\n    if b:\n        pass\nprint(1)";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        long indentCount = tokens.stream().filter(t -> t.tokenType() == TokenType.INDENT).count();
        long dedentCount = tokens.stream().filter(t -> t.tokenType() == TokenType.DEDENT).count();
        assertEquals(2, indentCount);
        assertEquals(2, dedentCount);

        // Verify print(1) is preceded by two DEDENT tokens
        int printIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).lexeme().equals("print")) {
                printIndex = i;
                break;
            }
        }
        assertTrue(printIndex >= 2);
        assertEquals(TokenType.DEDENT, tokens.get(printIndex - 1).tokenType());
        assertEquals(TokenType.DEDENT, tokens.get(printIndex - 2).tokenType());
    }

    @Test
    @DisplayName("Blank lines and comment-only lines do not generate indentation tokens")
    void testBlankLinesAndCommentsInIndentation() {
        String code = "def test():\n\n    # comment line\n    x = 10\n";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        long indentCount = tokens.stream().filter(t -> t.tokenType() == TokenType.INDENT).count();
        long dedentCount = tokens.stream().filter(t -> t.tokenType() == TokenType.DEDENT).count();
        assertEquals(1, indentCount);
        assertEquals(1, dedentCount);

        assertTrue(tokens.stream().anyMatch(t -> t.tokenType() == TokenType.COMMENT && t.lexeme().equals("# comment line")));
    }

    @Test
    @DisplayName("Implicit line joining inside parentheses, brackets, and braces suppresses NEWLINE and INDENT")
    void testBracketImplicitLineJoining() {
        String code = "items = [\n    1,\n    2\n]\nx = 3";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        // Inside [ ... ], no INDENT or DEDENT tokens should be emitted!
        long indentCount = tokens.stream().filter(t -> t.tokenType() == TokenType.INDENT).count();
        assertEquals(0, indentCount);
    }

    @Test
    @DisplayName("Explicit line continuation with backslash suppresses NEWLINE")
    void testBackslashLineContinuation() {
        String code = "x = 1 + \\\n    2";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        long newlineCount = tokens.stream().filter(t -> t.tokenType() == TokenType.NEWLINE).count();
        assertEquals(1, newlineCount); // only final EOF newline
    }

    @Test
    @DisplayName("Tokenizes single, double, triple-quoted strings and prefixes")
    void testPythonStrings() {
        String code = "'single' \"double\" '''triple single''' \"\"\"triple\ndouble\"\"\" f\"formatted\" r'raw' b\"bytes\" rf\"twochar\"";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(8, tokens.stream().filter(t -> t.tokenType() == TokenType.LITERAL_STRING).count());
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("'single'")));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("\"double\"")));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("'''triple single'''")));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().contains("triple\ndouble")));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("f\"formatted\"")));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("r'raw'")));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("b\"bytes\"")));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("rf\"twochar\"")));
    }

    @Test
    @DisplayName("Preserves unterminated string literal on unclosed quote")
    void testUnterminatedString() {
        String code = "x = \"unclosed string";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        Token unclosed = tokens.stream().filter(t -> t.tokenType() == TokenType.LITERAL_STRING).findFirst().orElseThrow();
        assertEquals("\"unclosed string", unclosed.lexeme());
    }

    @Test
    @DisplayName("Tokenizes Python numbers including hex, binary, octal, float, and imaginary")
    void testPythonNumbers() {
        String code = "123 0x1A 0o77 0b101 3.14 1e-5 2j 3.14j";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(8, tokens.stream().filter(t -> t.tokenType() == TokenType.LITERAL_NUMBER).count());
    }

    @Test
    @DisplayName("Tokenizes Python operators and delimiters including := and ->")
    void testOperatorsAndDelimiters() {
        String code = "def add(x: int) -> int:\n    y := x ** 2 // 3";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("->") && t.tokenType() == TokenType.DELIMITER));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals(":") && t.tokenType() == TokenType.DELIMITER));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals(":=") && t.tokenType() == TokenType.OPERATOR));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("**") && t.tokenType() == TokenType.OPERATOR));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("//") && t.tokenType() == TokenType.OPERATOR));
    }

    @Test
    @DisplayName("Preserves unknown characters")
    void testUnknownCharacters() {
        String code = "x = $ ` ? 1";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("$") && t.tokenType() == TokenType.UNKNOWN));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("`") && t.tokenType() == TokenType.UNKNOWN));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("?") && t.tokenType() == TokenType.UNKNOWN));
    }

    @Test
    @DisplayName("Rejects null payload or non-Python language")
    void testInvariants() {
        assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null));
        assertThrows(IllegalArgumentException.class, () ->
            tokenizer.tokenize(SourcePayload.snippet("class A {}", Language.JAVA))
        );
        assertTrue(tokenizer.tokenize(SourcePayload.snippet("", Language.PYTHON)).isEmpty());
    }
}
