package com.writely.syntax_analyzer.core.check;

import com.writely.syntax_analyzer.core.tokenization.Tokenizer;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StringAndCharacterLiteralChecker Tests (Issue #20 / ADR 0007)")
class StringAndCharacterLiteralCheckerTest {

    private final StringAndCharacterLiteralChecker checker = new StringAndCharacterLiteralChecker();

    private List<Token> tokenize(SourcePayload payload) {
        return Tokenizer.forLanguage(payload.language()).tokenize(payload);
    }

    @Nested
    @DisplayName("Contract and Invariant Tests")
    class ContractAndInvariantTests {

        @Test
        @DisplayName("Returns LITERAL_SYNTAX check category")
        void testCategory() {
            assertEquals(CheckCategory.LITERAL_SYNTAX, checker.category());
        }

        @Test
        @DisplayName("Rejects null payload or null tokens")
        void testNullValidations() {
            SourcePayload payload = SourcePayload.singleLine("String s = \"hello\";", Language.JAVA);
            List<Token> tokens = List.of();

            assertThrows(NullPointerException.class, () -> checker.check(null, tokens));
            assertThrows(NullPointerException.class, () -> checker.check(payload, null));
        }

        @Test
        @DisplayName("Returns empty unmodifiable list on empty tokens")
        void testEmptyTokens() {
            SourcePayload payload = SourcePayload.snippet("", Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, Collections.emptyList());
            assertTrue(diagnostics.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> diagnostics.add(null));
        }

        @Test
        @DisplayName("Returns empty diagnostics when no string or char literals exist")
        void testNoLiteralsPresent() {
            SourcePayload payload = SourcePayload.singleLine("int x = 42 + y;", Language.JAVA);
            List<Token> tokens = tokenize(payload);
            List<Diagnostic> diagnostics = checker.check(payload, tokens);
            assertTrue(diagnostics.isEmpty());
        }
    }

    @Nested
    @DisplayName("Valid Literals Across Java, Python, and C++")
    class ValidLiteralsTests {

        @Test
        @DisplayName("Java: Valid strings, text blocks, and character literals produce zero diagnostics")
        void testJavaValidLiterals() {
            String code = """
                public class ValidLiterals {
                    String s1 = "Hello, World!";
                    String s2 = "Escapes: \\b \\t \\n \\f \\r \\" \\' \\\\ \\s";
                    String s3 = "Octal: \\0 \\12 \\377";
                    String s4 = "Unicode: \\u0041 \\u1234 \\uuuu0041";
                    String block = \"\"\"
                        multi-line
                        text block
                        \\"\\"\\"
                        \"\"\";
                    char c1 = 'a';
                    char c2 = '\\n';
                    char c3 = '\\t';
                    char c4 = '\\\\';
                    char c5 = '\\'';
                    char c6 = '\\u0041';
                    char c7 = '\\101';
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics, got: " + diagnostics);
        }

        @Test
        @DisplayName("Python: Valid single, double, triple-quoted, and raw strings produce zero diagnostics")
        void testPythonValidLiterals() {
            String code = """
                s1 = "Hello, World!"
                s2 = 'Single quote string'
                s3 = '''Triple single quoted'''
                s4 = \"\"\"Triple double quoted\"\"\"
                s5 = "Escapes: \\a \\b \\f \\n \\r \\t \\v \\\\ \\' \\""
                s6 = "Hex & Unicode: \\x41 \\u0041 \\U00000041"
                s7 = "Octal: \\0 \\12 \\777"
                raw1 = r"C:\\Users\\test\\new"
                raw2 = R'Raw with \\c and \\d illegal escapes exempted'
                raw3 = rf\"\"\"Raw formatted triple\"\"\"
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics, got: " + diagnostics);
        }

        @Test
        @DisplayName("C++: Valid standard strings, char literals, and raw strings produce zero diagnostics")
        void testCppValidLiterals() {
            String code = """
                #include <iostream>
                int main() {
                    const char* s1 = "Hello, World!";
                    const char* s2 = "Escapes: \\' \\\" \\? \\\\ \\a \\b \\f \\n \\r \\t \\v";
                    const char* s3 = "Hex & Universal: \\x41 \\u0041 \\U00000041";
                    const char* s4 = "Octal: \\0 \\12 \\77";
                    const char* raw1 = R"(Raw string with \\c \\u123 exempted)";
                    const char* raw2 = R"custom(Another raw \\c string)custom";
                    char c1 = 'x';
                    char c2 = '\\n';
                    char c3 = '\\\\';
                    char c4 = '\\'';
                    char c5 = '\\u0041';
                    char c6 = '\\101';
                    return 0;
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics, got: " + diagnostics);
        }
    }

    @Nested
    @DisplayName("Unclosed String Literals Tests (ERR_UNCLOSED_STRING_LITERAL)")
    class UnclosedStringLiteralsTests {

        @Test
        @DisplayName("Java: Detects unclosed standard string literal")
        void testJavaUnclosedStandardString() {
            String code = "String s = \"unclosed string;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, diag.code());
            assertEquals(Severity.ERROR, diag.severity());
            assertEquals(CheckCategory.LITERAL_SYNTAX, diag.category());
            assertEquals(1, diag.location().line());
            assertEquals(12, diag.location().column()); // '"' starts at column 12
            assertTrue(diag.message().contains("Unclosed string literal"));
            assertTrue(diag.suggestedFix().isPresent());
        }

        @Test
        @DisplayName("Java: Detects unclosed string with trailing escaped quote")
        void testJavaUnclosedStringWithEscapedQuote() {
            String code = "String s = \"unclosed with escaped quote\\\";";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, diag.code());
            assertEquals(1, diag.location().line());
        }

        @Test
        @DisplayName("Java: Detects unclosed text block literal")
        void testJavaUnclosedTextBlock() {
            String code = """
                String block = \"\"\"
                    line 1
                    line 2
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, diag.code());
            assertEquals(1, diag.location().line());
            assertTrue(diag.message().contains("text block"));
        }

        @Test
        @DisplayName("Python: Detects unclosed single-quoted and double-quoted strings")
        void testPythonUnclosedQuotes() {
            String code1 = "s = 'unclosed single quote";
            SourcePayload p1 = SourcePayload.singleLine(code1, Language.PYTHON);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, d1.get(0).code());

            String code2 = "s = \"unclosed double quote";
            SourcePayload p2 = SourcePayload.singleLine(code2, Language.PYTHON);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertEquals(1, d2.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, d2.get(0).code());
        }

        @Test
        @DisplayName("Python: Detects unclosed triple-quoted strings")
        void testPythonUnclosedTripleQuotes() {
            String code1 = "s = '''unclosed triple single";
            SourcePayload p1 = SourcePayload.singleLine(code1, Language.PYTHON);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, d1.get(0).code());

            String code2 = "s = \"\"\"unclosed triple double";
            SourcePayload p2 = SourcePayload.singleLine(code2, Language.PYTHON);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertEquals(1, d2.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, d2.get(0).code());
        }

        @Test
        @DisplayName("C++: Detects unclosed standard string literal")
        void testCppUnclosedStandardString() {
            String code = "const char* s = \"unclosed string;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, diag.code());
            assertEquals(1, diag.location().line());
        }

        @Test
        @DisplayName("C++: Detects unclosed raw string literals")
        void testCppUnclosedRawStrings() {
            String code1 = "const char* s = R\"(unclosed raw string;";
            SourcePayload p1 = SourcePayload.singleLine(code1, Language.CPP);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, d1.get(0).code());

            String code2 = "const char* s = R\"delim(unclosed with delim;";
            SourcePayload p2 = SourcePayload.singleLine(code2, Language.CPP);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertEquals(1, d2.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, d2.get(0).code());
        }
    }

    @Nested
    @DisplayName("Character Literal Tests (ERR_UNCLOSED, ERR_EMPTY, ERR_INVALID)")
    class CharacterLiteralTests {

        @Test
        @DisplayName("Java: Detects empty character literal ''")
        void testJavaEmptyCharLiteral() {
            String code = "char c = '';";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(StringAndCharacterLiteralChecker.ERR_EMPTY_CHARACTER_LITERAL, diag.code());
            assertEquals(Severity.ERROR, diag.severity());
            assertEquals(CheckCategory.LITERAL_SYNTAX, diag.category());
            assertEquals(1, diag.location().line());
            assertEquals(10, diag.location().column()); // '' starts at col 10
            assertTrue(diag.message().contains("Empty character literal"));
            assertTrue(diag.suggestedFix().isPresent());
        }

        @Test
        @DisplayName("C++: Detects empty character literal ''")
        void testCppEmptyCharLiteral() {
            String code = "char c = '';";
            SourcePayload payload = SourcePayload.singleLine(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(StringAndCharacterLiteralChecker.ERR_EMPTY_CHARACTER_LITERAL, diag.code());
            assertEquals(1, diag.location().line());
        }

        @Test
        @DisplayName("Java: Detects unclosed character literal 'a")
        void testJavaUnclosedCharLiteral() {
            String code = "char c = 'a;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_CHARACTER_LITERAL, diag.code());
            assertEquals(1, diag.location().line());
            assertEquals(10, diag.location().column());
            assertTrue(diag.message().contains("Unclosed character literal"));
            assertTrue(diag.suggestedFix().isPresent());
        }

        @Test
        @DisplayName("Java: Detects unclosed character literal with escaped quote '\\'")
        void testJavaUnclosedCharWithEscapedQuote() {
            String code = "char c = '\\';";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_CHARACTER_LITERAL, diag.code());
        }

        @Test
        @DisplayName("Java: Detects invalid multi-character literal 'ab' and 'abc'")
        void testJavaMultiCharLiteral() {
            String code1 = "char c = 'ab';";
            SourcePayload p1 = SourcePayload.singleLine(code1, Language.JAVA);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_INVALID_CHARACTER_LITERAL, d1.get(0).code());
            assertEquals(1, d1.get(0).location().line());
            assertTrue(d1.get(0).message().contains("multiple characters"));

            String code2 = "char c = 'abc';";
            SourcePayload p2 = SourcePayload.singleLine(code2, Language.JAVA);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertEquals(1, d2.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_INVALID_CHARACTER_LITERAL, d2.get(0).code());
        }

        @Test
        @DisplayName("C++: Detects invalid multi-character literal 'ab'")
        void testCppMultiCharLiteral() {
            String code = "char c = 'ab';";
            SourcePayload payload = SourcePayload.singleLine(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(StringAndCharacterLiteralChecker.ERR_INVALID_CHARACTER_LITERAL, diag.code());
        }

        @Test
        @DisplayName("Python: Flagged as ERR_INVALID_CHARACTER_LITERAL when Token has LITERAL_CHAR")
        void testPythonCharTokenWhenUsedAsChar() {
            SourceLocation loc = SourceLocation.of(1, 1, 0);
            Token token = Token.of(TokenType.LITERAL_CHAR, "'ab'", SourceSpan.point(loc));
            SourcePayload payload = SourcePayload.singleLine("'ab'", Language.PYTHON);

            List<Diagnostic> diagnostics = checker.check(payload, List.of(token));
            assertEquals(1, diagnostics.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_INVALID_CHARACTER_LITERAL, diagnostics.get(0).code());
        }
    }

    @Nested
    @DisplayName("Illegal Escape Sequences Tests (ERR_ILLEGAL_ESCAPE_SEQUENCE)")
    class IllegalEscapeSequencesTests {

        @Test
        @DisplayName("Java: Detects illegal escape \\c and malformed unicode in string literal")
        void testJavaIllegalEscapesInString() {
            String code1 = "String s = \"hello \\c world\";";
            SourcePayload p1 = SourcePayload.singleLine(code1, Language.JAVA);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, d1.get(0).code());
            assertEquals(1, d1.get(0).location().line());
            assertTrue(d1.get(0).message().contains("\\c"));

            // Malformed unicode \\u123 (only 3 hex digits)
            String code2 = "String s = \"test \\" + "u123 value\";";
            SourcePayload p2 = SourcePayload.singleLine(code2, Language.JAVA);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertEquals(1, d2.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, d2.get(0).code());
            assertTrue(d2.get(0).message().contains("unicode"));

            // Invalid octal \8
            String code3 = "String s = \"bad octal \\8\";";
            SourcePayload p3 = SourcePayload.singleLine(code3, Language.JAVA);
            List<Diagnostic> d3 = checker.check(p3, tokenize(p3));
            assertEquals(1, d3.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, d3.get(0).code());
        }

        @Test
        @DisplayName("Java: Detects illegal escape \\c and malformed unicode in character literal")
        void testJavaIllegalEscapeInChar() {
            String code1 = "char c = '\\c';";
            SourcePayload p1 = SourcePayload.singleLine(code1, Language.JAVA);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, d1.get(0).code());

            String code2 = "char c = '\\" + "u123';";
            SourcePayload p2 = SourcePayload.singleLine(code2, Language.JAVA);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertEquals(1, d2.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, d2.get(0).code());
        }

        @Test
        @DisplayName("Python: Detects illegal escape \\c in standard string but exempts raw string")
        void testPythonIllegalEscapeVsRawString() {
            String codeInvalid = "s = \"invalid \\c escape\"";
            SourcePayload p1 = SourcePayload.singleLine(codeInvalid, Language.PYTHON);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, d1.get(0).code());

            // Raw strings with r or R prefix are exempt
            String codeRaw1 = "s = r\"raw \\c escape\"";
            SourcePayload p2 = SourcePayload.singleLine(codeRaw1, Language.PYTHON);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertTrue(d2.isEmpty(), () -> "Expected 0 diagnostics for raw string, got: " + d2);

            String codeRaw2 = "s = R'raw single \\c \\d'";
            SourcePayload p3 = SourcePayload.singleLine(codeRaw2, Language.PYTHON);
            List<Diagnostic> d3 = checker.check(p3, tokenize(p3));
            assertTrue(d3.isEmpty(), () -> "Expected 0 diagnostics for raw string, got: " + d3);
        }

        @Test
        @DisplayName("C++: Detects illegal escape \\c in standard string but exempts raw string")
        void testCppIllegalEscapeVsRawString() {
            String codeInvalid = "const char* s = \"invalid \\c escape\";";
            SourcePayload p1 = SourcePayload.singleLine(codeInvalid, Language.CPP);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, d1.get(0).code());

            // C++ raw string R"(...)" is exempt
            String codeRaw = "const char* s = R\"(raw \\c escape)\";";
            SourcePayload p2 = SourcePayload.singleLine(codeRaw, Language.CPP);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertTrue(d2.isEmpty(), () -> "Expected 0 diagnostics for C++ raw string, got: " + d2);
        }

        @Test
        @DisplayName("C++: Detects malformed hex and unicode escapes in standard literals")
        void testCppMalformedHexAndUnicode() {
            String codeHex = "const char* s = \"bad hex \\x\";";
            SourcePayload p1 = SourcePayload.singleLine(codeHex, Language.CPP);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, d1.get(0).code());

            String codeUni = "const char* s = \"bad uni \\" + "u123\";";
            SourcePayload p2 = SourcePayload.singleLine(codeUni, Language.CPP);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertEquals(1, d2.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, d2.get(0).code());
        }
    }

    @Nested
    @DisplayName("Multiple Errors in Single Snippet with Clean Recovery")
    class MultipleErrorsAndCleanRecoveryTests {

        @Test
        @DisplayName("Java: Multiple literal errors across single file recover cleanly")
        void testJavaMultipleErrorsRecovery() {
            String code = """
                String s1 = "unclosed;
                char c1 = '';
                char c2 = 'ab';
                String s2 = "invalid \\c escape";
                char c3 = 'unclosed;
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(5, diagnostics.size());

            // 1. Line 1: unclosed string
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, diagnostics.get(0).code());
            assertEquals(1, diagnostics.get(0).location().line());

            // 2. Line 2: empty char literal
            assertEquals(StringAndCharacterLiteralChecker.ERR_EMPTY_CHARACTER_LITERAL, diagnostics.get(1).code());
            assertEquals(2, diagnostics.get(1).location().line());

            // 3. Line 3: invalid multi-character literal
            assertEquals(StringAndCharacterLiteralChecker.ERR_INVALID_CHARACTER_LITERAL, diagnostics.get(2).code());
            assertEquals(3, diagnostics.get(2).location().line());

            // 4. Line 4: illegal escape sequence
            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, diagnostics.get(3).code());
            assertEquals(4, diagnostics.get(3).location().line());

            // 5. Line 5: unclosed character literal
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_CHARACTER_LITERAL, diagnostics.get(4).code());
            assertEquals(5, diagnostics.get(4).location().line());
        }

        @Test
        @DisplayName("Python: Multiple literal defects in single snippet recover cleanly")
        void testPythonMultipleErrorsRecovery() {
            String code = """
                s1 = "unclosed double
                s2 = "invalid \\c escape"
                s3 = '''unclosed triple quote
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(3, diagnostics.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, diagnostics.get(0).code());
            assertEquals(1, diagnostics.get(0).location().line());

            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, diagnostics.get(1).code());
            assertEquals(2, diagnostics.get(1).location().line());

            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, diagnostics.get(2).code());
            assertEquals(3, diagnostics.get(2).location().line());
        }

        @Test
        @DisplayName("C++: Multiple literal defects in single snippet recover cleanly")
        void testCppMultipleErrorsRecovery() {
            String code = """
                const char* s1 = "unclosed string;
                char c1 = '';
                char c2 = 'ab';
                const char* s2 = "invalid \\c escape";
                const char* s3 = R"(unclosed raw string;
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(5, diagnostics.size());
            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, diagnostics.get(0).code());
            assertEquals(1, diagnostics.get(0).location().line());

            assertEquals(StringAndCharacterLiteralChecker.ERR_EMPTY_CHARACTER_LITERAL, diagnostics.get(1).code());
            assertEquals(2, diagnostics.get(1).location().line());

            assertEquals(StringAndCharacterLiteralChecker.ERR_INVALID_CHARACTER_LITERAL, diagnostics.get(2).code());
            assertEquals(3, diagnostics.get(2).location().line());

            assertEquals(StringAndCharacterLiteralChecker.ERR_ILLEGAL_ESCAPE_SEQUENCE, diagnostics.get(3).code());
            assertEquals(4, diagnostics.get(3).location().line());

            assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, diagnostics.get(4).code());
            assertEquals(5, diagnostics.get(4).location().line());
        }
    }
}
