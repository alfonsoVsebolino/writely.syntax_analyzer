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

@DisplayName("CppTokenizer Tests")
class CppTokenizerTest {

    private final CppTokenizer tokenizer = new CppTokenizer();

    @Test
    @DisplayName("Tokenizes C++ keywords including constexpr, nullptr, auto, template, virtual")
    void testKeywords() {
        String code = "constexpr auto ptr = nullptr; template<typename T> class Foo : public Bar { virtual void f() noexcept; };";
        SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("constexpr") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("auto") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("nullptr") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("template") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("typename") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("virtual") && t.tokenType() == TokenType.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("noexcept") && t.tokenType() == TokenType.KEYWORD));
    }

    @Test
    @DisplayName("Tokenizes preprocessor directives as trivia with PREPROCESSOR category")
    void testPreprocessorDirectives() {
        String code = "#include <iostream>\n#define MAX 100\nint x = MAX;";
        SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
        List<Token> tokens = tokenizer.tokenize(payload);

        Token includeDir = tokens.get(0);
        assertEquals(TokenType.COMMENT, includeDir.tokenType());
        assertTrue(includeDir.tokenType().isTrivia());
        assertEquals("PREPROCESSOR", includeDir.category());
        assertEquals("#include <iostream>", includeDir.lexeme());
        assertEquals(SourceLocation.of(1, 1, 0), includeDir.startLocation());

        Token defineDir = tokens.get(1);
        assertEquals(TokenType.COMMENT, defineDir.tokenType());
        assertEquals("PREPROCESSOR", defineDir.category());
        assertEquals("#define MAX 100", defineDir.lexeme());

        Token kw = tokens.get(2);
        assertEquals("int", kw.lexeme());
        assertEquals(TokenType.KEYWORD, kw.tokenType());
        assertEquals(3, kw.startLocation().line());
    }

    @Test
    @DisplayName("Preprocessor directives support backslash line continuations")
    void testPreprocessorLineContinuation() {
        String code = "#define MULTI \\\n    100\nint a;";
        SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
        List<Token> tokens = tokenizer.tokenize(payload);

        Token dir = tokens.get(0);
        assertEquals("PREPROCESSOR", dir.category());
        assertTrue(dir.lexeme().contains("MULTI"));
        assertTrue(dir.lexeme().contains("100"));

        Token kw = tokens.get(1);
        assertEquals("int", kw.lexeme());
        assertEquals(3, kw.startLocation().line());
    }

    @Test
    @DisplayName("Tokenizes C++ operators: ::, ->, ->*, .*, <=>, ++, --, etc.")
    void testCppOperators() {
        String code = "std::vector<int> v; ptr->member; obj.*m; p->*m; a <=> b; ++i; x <<= 1;";
        SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("::") && t.tokenType() == TokenType.OPERATOR));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("->") && t.tokenType() == TokenType.OPERATOR));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals(".*") && t.tokenType() == TokenType.OPERATOR));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("->*") && t.tokenType() == TokenType.OPERATOR));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("<=>") && t.tokenType() == TokenType.OPERATOR));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("++") && t.tokenType() == TokenType.OPERATOR));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("<<=") && t.tokenType() == TokenType.OPERATOR));
    }

    @Test
    @DisplayName("Tokenizes raw string literals R\"(...)\" and R\"delim(...)delim\"")
    void testRawStringLiterals() {
        String code = "R\"(hello \"world\" \n multiline)\" R\"foo(custom)foo\"";
        SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(2, tokens.size());
        Token raw1 = tokens.get(0);
        assertEquals(TokenType.LITERAL_STRING, raw1.tokenType());
        assertTrue(raw1.lexeme().startsWith("R\"("));
        assertTrue(raw1.lexeme().endsWith(")\""));

        Token raw2 = tokens.get(1);
        assertEquals(TokenType.LITERAL_STRING, raw2.tokenType());
        assertEquals("R\"foo(custom)foo\"", raw2.lexeme());
    }

    @Test
    @DisplayName("Tokenizes C++ numeric literals with single quote separators and suffixes")
    void testNumericLiterals() {
        String code = "1'000'000 0x1A'FF 0b1010'0101 42ULL 3.14f 1e-5L";
        SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(6, tokens.size());
        for (Token t : tokens) {
            assertEquals(TokenType.LITERAL_NUMBER, t.tokenType());
        }
        assertEquals("1'000'000", tokens.get(0).lexeme());
        assertEquals("0x1A'FF", tokens.get(1).lexeme());
        assertEquals("0b1010'0101", tokens.get(2).lexeme());
        assertEquals("42ULL", tokens.get(3).lexeme());
        assertEquals("3.14f", tokens.get(4).lexeme());
        assertEquals("1e-5L", tokens.get(5).lexeme());
    }

    @Test
    @DisplayName("Tokenizes C++ comments and isolates syntax within them")
    void testCommentsAndTriviaIsolation() {
        String code = "// int x = 10; < >\n/* float y = 20.0; :: -> */\nint z;";
        SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertEquals(5, tokens.size());
        assertEquals(TokenType.COMMENT, tokens.get(0).tokenType());
        assertEquals(TokenType.COMMENT, tokens.get(1).tokenType());
        assertEquals(TokenType.KEYWORD, tokens.get(2).tokenType());
        assertEquals("int", tokens.get(2).lexeme());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).tokenType());
        assertEquals("z", tokens.get(3).lexeme());
        assertEquals(TokenType.DELIMITER, tokens.get(4).tokenType());
        assertEquals(";", tokens.get(4).lexeme());

        // Unterminated block comment
        List<Token> unterminated = tokenizer.tokenize(SourcePayload.snippet("/* unclosed", Language.CPP));
        assertEquals(1, unterminated.size());
        assertEquals("/* unclosed", unterminated.get(0).lexeme());

        // Character literal with escape
        List<Token> charTokens = tokenizer.tokenize(SourcePayload.snippet("'\\'' '\\n'", Language.CPP));
        assertEquals(2, charTokens.size());
        assertEquals(TokenType.LITERAL_CHAR, charTokens.get(0).tokenType());

        // Hex and binary numbers
        List<Token> numTokens = tokenizer.tokenize(SourcePayload.snippet("0x1A 0b1010", Language.CPP));
        assertEquals(2, numTokens.size());
        assertEquals(TokenType.LITERAL_NUMBER, numTokens.get(0).tokenType());
        assertEquals(TokenType.LITERAL_NUMBER, numTokens.get(1).tokenType());

        // Colon delimiter
        List<Token> colonTokens = tokenizer.tokenize(SourcePayload.snippet("public:", Language.CPP));
        assertEquals(2, colonTokens.size());
        assertEquals(TokenType.KEYWORD, colonTokens.get(0).tokenType());
        assertEquals(TokenType.DELIMITER, colonTokens.get(1).tokenType());
        assertEquals(":", colonTokens.get(1).lexeme());
    }

    @Test
    @DisplayName("Exact coordinate tracking across multiline C++ input")
    void testCoordinatesAcrossLines() {
        String code = "int a = 1;\nint b = 2;";
        SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
        List<Token> tokens = tokenizer.tokenize(payload);

        Token intB = tokens.get(5);
        assertEquals("int", intB.lexeme());
        assertEquals(SourceLocation.of(2, 1, 11), intB.startLocation());
        assertEquals(SourceLocation.of(2, 4, 14), intB.endLocation());
    }

    @Test
    @DisplayName("Preserves unknown characters as UNKNOWN tokens")
    void testUnknownCharacters() {
        String code = "int x = @ ` $;";
        SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
        List<Token> tokens = tokenizer.tokenize(payload);

        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("@") && t.tokenType() == TokenType.UNKNOWN));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("`") && t.tokenType() == TokenType.UNKNOWN));
        assertTrue(tokens.stream().anyMatch(t -> t.lexeme().equals("$") && t.tokenType() == TokenType.UNKNOWN));
    }

    @Test
    @DisplayName("Rejects null payload or non-Cpp language")
    void testInvariants() {
        assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null));
        assertThrows(IllegalArgumentException.class, () ->
            tokenizer.tokenize(SourcePayload.snippet("class A {}", Language.JAVA))
        );
        assertTrue(tokenizer.tokenize(SourcePayload.snippet("", Language.CPP)).isEmpty());
    }
}
