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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DelimiterMatchingChecker Tests (Issue #19 / ADR 0005)")
class DelimiterMatchingCheckerTest {

    private final DelimiterMatchingChecker checker = new DelimiterMatchingChecker();

    private List<Token> tokenize(SourcePayload payload) {
        return Tokenizer.forLanguage(payload.language()).tokenize(payload);
    }

    @Nested
    @DisplayName("Contract and Invariant Tests")
    class ContractAndInvariantTests {

        @Test
        @DisplayName("Returns DELIMITER_MATCH check category")
        void testCategory() {
            assertEquals(CheckCategory.DELIMITER_MATCH, checker.category());
        }

        @Test
        @DisplayName("Rejects null payload or null tokens")
        void testNullValidations() {
            SourcePayload payload = SourcePayload.singleLine("int x = 1;", Language.JAVA);
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
        @DisplayName("Returns empty diagnostics when no target delimiters are present")
        void testNoDelimiters() {
            SourcePayload payload = SourcePayload.singleLine("int x = 42;", Language.JAVA);
            List<Token> tokens = tokenize(payload);
            List<Diagnostic> diagnostics = checker.check(payload, tokens);
            assertTrue(diagnostics.isEmpty());
        }

        @Test
        @DisplayName("Validates delimiter pairing and helper invariants")
        void testHelperMethodsInvariants() {
            assertTrue(DelimiterMatchingChecker.isDelimiterPair("(", ")"));
            assertTrue(DelimiterMatchingChecker.isDelimiterPair("[", "]"));
            assertTrue(DelimiterMatchingChecker.isDelimiterPair("{", "}"));
            assertFalse(DelimiterMatchingChecker.isDelimiterPair("(", "]"));
            assertFalse(DelimiterMatchingChecker.isDelimiterPair("<", ">"));

            assertEquals(")", DelimiterMatchingChecker.matchingClosing("("));
            assertEquals("]", DelimiterMatchingChecker.matchingClosing("["));
            assertEquals("}", DelimiterMatchingChecker.matchingClosing("{"));
            assertThrows(IllegalArgumentException.class, () -> DelimiterMatchingChecker.matchingClosing("<"));
        }
    }

    @Nested
    @DisplayName("Valid Balanced Cases Across Java, Python, and C++")
    class BalancedCasesTests {

        @Test
        @DisplayName("Java: Simple and complex nested structures are balanced")
        void testJavaBalanced() {
            String code = """
                public class MatrixCalc {
                    public int[] process(int[][] data) {
                        if ((data.length > 0) && (data[0].length > 0)) {
                            Runnable task = () -> {
                                int val = data[0][(0)];
                            };
                            return new int[]{ (data[0][0] + 1) * 2 };
                        }
                        return new int[]{};
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics but got: " + diagnostics);
        }

        @Test
        @DisplayName("Python: Simple and complex nested structures are balanced")
        void testPythonBalanced() {
            String code = """
                def process_data(items):
                    mapping = {"key": [1, (2, 3), {4: 5}], "empty": {}}
                    grid = [[(x, y) for x in range(3)] for y in range(3)]
                    if len(items) > 0 and (items[0] is not None):
                        return mapping["key"][1][0]
                    return None
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics but got: " + diagnostics);
        }

        @Test
        @DisplayName("C++: Simple and complex nested structures are balanced")
        void testCppBalanced() {
            String code = """
                #include <vector>
                struct Widget { int x; int y; };
                int main() {
                    int arr[] = {1, 2, 3};
                    auto lambda = [](int a) { return (a * [=]() { return 2; }()); };
                    for (int i = 0; i < 3; ++i) {
                        if ((arr[i] > 0)) {
                            int val = arr[(i)];
                        }
                    }
                    return 0;
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics but got: " + diagnostics);
        }
    }

    @Nested
    @DisplayName("Trivia and Literals Isolation (Zero False Positives)")
    class TriviaAndLiteralsIsolationTests {

        @Test
        @DisplayName("Java: Delimiters inside comments (// }, /* ( */) produce zero false positives")
        void testJavaCommentsContainingBrackets() {
            String code = """
                public class CommentTest {
                    // } ] )
                    /* ( [ { */
                    /* multiline with unclosed { and [ and ( */
                    void test() {
                        int x = (1 + 2); // }
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "False positives in comments: " + diagnostics);
        }

        @Test
        @DisplayName("Python: Delimiters inside comments (# ]) produce zero false positives")
        void testPythonCommentsContainingBrackets() {
            String code = """
                # ] } )
                def compute():
                    # ( [ {
                    x = (10 + 20)  # ] )
                    return [x]  # { (
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "False positives in comments: " + diagnostics);
        }

        @Test
        @DisplayName("C++: Delimiters inside comments (// }, /* ( */) produce zero false positives")
        void testCppCommentsContainingBrackets() {
            String code = """
                // } ] )
                /* ( [ { */
                int main() {
                    // }
                    /* { [ ( */
                    return (0);
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "False positives in comments: " + diagnostics);
        }

        @Test
        @DisplayName("Java: Delimiters inside string/char literals (\"{\", '[') produce zero false positives")
        void testJavaStringLiteralsContainingBrackets() {
            String code = """
                public class LiteralTest {
                    String s1 = "{";
                    String s2 = "}";
                    char c1 = '[';
                    char c2 = ']';
                    char c3 = '(';
                    char c4 = ')';
                    String complex = "text with ( [ { ) ] } mixed";
                    void run() {
                        System.out.println("([{");
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "False positives in literals: " + diagnostics);
        }

        @Test
        @DisplayName("Python: Delimiters inside string literals (\"{\", '[') produce zero false positives")
        void testPythonStringLiteralsContainingBrackets() {
            String code = """
                s1 = "{"
                s2 = '}'
                s3 = '['
                s4 = ']'
                s5 = "([{"
                triple = \"\"\"( [ { ) ] }\"\"\"
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "False positives in literals: " + diagnostics);
        }

        @Test
        @DisplayName("C++: Delimiters inside string/char literals produce zero false positives")
        void testCppStringLiteralsContainingBrackets() {
            String code = """
                const char* s1 = "{";
                const char* s2 = "}";
                char c1 = '[';
                char c2 = ']';
                const char* s3 = "( [ { ) ] }";
                int main() { return 0; }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), () -> "False positives in literals: " + diagnostics);
        }
    }

    @Nested
    @DisplayName("Mismatched Delimiters Tests: (], {], [)")
    class MismatchedDelimitersTests {

        @Test
        @DisplayName("Detects mismatched delimiter (]")
        void testMismatchParenSquare() {
            String code = "int x = (1 + 2];";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diag.code());
            assertEquals(Severity.ERROR, diag.severity());
            assertEquals(CheckCategory.DELIMITER_MATCH, diag.category());
            assertEquals(1, diag.location().line());
            assertEquals(15, diag.location().column()); // ']' at index 14 -> 1-based column 15
            assertTrue(diag.message().contains("expected ')'"));
            assertTrue(diag.message().contains("found ']'"));
            assertTrue(diag.suggestedFix().isPresent());
            assertEquals("Replace ']' with ')'", diag.suggestedFix().get());
        }

        @Test
        @DisplayName("Detects mismatched delimiter {]")
        void testMismatchBraceSquare() {
            String code = "int[] arr = {1, 2];";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diag.code());
            assertEquals(Severity.ERROR, diag.severity());
            assertEquals(CheckCategory.DELIMITER_MATCH, diag.category());
            assertEquals(1, diag.location().line());
            assertEquals(18, diag.location().column()); // ']' at index 17 -> 1-based column 18
            assertTrue(diag.message().contains("expected '}'"));
            assertTrue(diag.message().contains("found ']'"));
            assertTrue(diag.suggestedFix().isPresent());
            assertEquals("Replace ']' with '}'", diag.suggestedFix().get());
        }

        @Test
        @DisplayName("Detects mismatched delimiter [)")
        void testMismatchSquareParen() {
            String code = "int x = arr[0);";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diag.code());
            assertEquals(Severity.ERROR, diag.severity());
            assertEquals(CheckCategory.DELIMITER_MATCH, diag.category());
            assertEquals(1, diag.location().line());
            assertEquals(14, diag.location().column()); // ')' at index 13 -> 1-based column 14
            assertTrue(diag.message().contains("expected ']'"));
            assertTrue(diag.message().contains("found ')'"));
            assertTrue(diag.suggestedFix().isPresent());
            assertEquals("Replace ')' with ']'", diag.suggestedFix().get());
        }

        @Test
        @DisplayName("Detects other mismatched pairs: {), (}, [}")
        void testOtherMismatches() {
            // {)
            SourcePayload p1 = SourcePayload.singleLine("int x = {10);", Language.JAVA);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, d1.get(0).code());
            assertTrue(d1.get(0).message().contains("expected '}'"));

            // (}
            SourcePayload p2 = SourcePayload.singleLine("int x = (10};", Language.JAVA);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertEquals(1, d2.size());
            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, d2.get(0).code());
            assertTrue(d2.get(0).message().contains("expected ')'"));

            // [}
            SourcePayload p3 = SourcePayload.singleLine("int x = [10};", Language.JAVA);
            List<Diagnostic> d3 = checker.check(p3, tokenize(p3));
            assertEquals(1, d3.size());
            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, d3.get(0).code());
            assertTrue(d3.get(0).message().contains("expected ']'"));
        }
    }

    @Nested
    @DisplayName("Unexpected Closing Delimiters Tests: foo() )")
    class UnexpectedClosingDelimitersTests {

        @Test
        @DisplayName("Detects unexpected closing delimiter: foo() )")
        void testUnexpectedClosingAfterBalancedCall() {
            String code = "foo() )";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(DelimiterMatchingChecker.ERR_UNEXPECTED_CLOSING_DELIMITER, diag.code());
            assertEquals(Severity.ERROR, diag.severity());
            assertEquals(CheckCategory.DELIMITER_MATCH, diag.category());
            assertEquals(1, diag.location().line());
            assertEquals(7, diag.location().column()); // second ')' at column 7
            assertTrue(diag.message().contains("Unexpected closing delimiter ')'"));
            assertTrue(diag.suggestedFix().isPresent());
            assertEquals("Remove unexpected ')'", diag.suggestedFix().get());
        }

        @Test
        @DisplayName("Detects unexpected closing delimiter on empty stack at start of file")
        void testUnexpectedClosingAtStart() {
            String code = ") int x = 1;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(DelimiterMatchingChecker.ERR_UNEXPECTED_CLOSING_DELIMITER, diag.code());
            assertEquals(1, diag.location().line());
            assertEquals(1, diag.location().column());
            assertEquals("Remove unexpected ')'", diag.suggestedFix().get());
        }

        @Test
        @DisplayName("Detects unexpected closing square bracket and curly brace")
        void testUnexpectedClosingBracketAndBrace() {
            SourcePayload p1 = SourcePayload.singleLine("int x = 10];", Language.JAVA);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(DelimiterMatchingChecker.ERR_UNEXPECTED_CLOSING_DELIMITER, d1.get(0).code());
            assertEquals("Remove unexpected ']'", d1.get(0).suggestedFix().get());

            SourcePayload p2 = SourcePayload.singleLine("int x = 10};", Language.JAVA);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertEquals(1, d2.size());
            assertEquals(DelimiterMatchingChecker.ERR_UNEXPECTED_CLOSING_DELIMITER, d2.get(0).code());
            assertEquals("Remove unexpected '}'", d2.get(0).suggestedFix().get());
        }
    }

    @Nested
    @DisplayName("Unclosed Delimiters at EOF Tests: public void test() {")
    class UnclosedDelimitersAtEofTests {

        @Test
        @DisplayName("Detects unclosed delimiter at EOF: public void test() {")
        void testUnclosedMethodBrace() {
            String code = "public void test() {";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diag.code());
            assertEquals(Severity.ERROR, diag.severity());
            assertEquals(CheckCategory.DELIMITER_MATCH, diag.category());
            assertEquals(1, diag.location().line());
            assertEquals(20, diag.location().column()); // '{' at column 20
            assertTrue(diag.message().contains("Unclosed delimiter '{'"));
            assertTrue(diag.message().contains("expected matching '}'"));
            assertTrue(diag.suggestedFix().isPresent());
            assertEquals("Insert matching '}'", diag.suggestedFix().get());
        }

        @Test
        @DisplayName("Detects unclosed parenthesis and square bracket at EOF")
        void testUnclosedParenAndBracket() {
            SourcePayload p1 = SourcePayload.singleLine("int x = (1 + 2;", Language.JAVA);
            List<Diagnostic> d1 = checker.check(p1, tokenize(p1));
            assertEquals(1, d1.size());
            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, d1.get(0).code());
            assertEquals("Insert matching ')'", d1.get(0).suggestedFix().get());

            SourcePayload p2 = SourcePayload.singleLine("int[] arr = new int[10;", Language.JAVA);
            List<Diagnostic> d2 = checker.check(p2, tokenize(p2));
            assertEquals(1, d2.size());
            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, d2.get(0).code());
            assertEquals("Insert matching ']'", d2.get(0).suggestedFix().get());
        }

        @Test
        @DisplayName("Detects multiple unclosed delimiters remaining on stack at EOF")
        void testMultipleUnclosedAtEof() {
            String code = """
                class A {
                    void b() {
                        if (true) {
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(3, diagnostics.size());
            for (Diagnostic diag : diagnostics) {
                assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diag.code());
                assertEquals(Severity.ERROR, diag.severity());
                assertEquals("Insert matching '}'", diag.suggestedFix().get());
            }
            assertEquals(1, diagnostics.get(0).location().line());
            assertEquals(2, diagnostics.get(1).location().line());
            assertEquals(3, diagnostics.get(2).location().line());
        }
    }

    @Nested
    @DisplayName("Recovery Lookback Window Tests (up to 3 levels)")
    class RecoveryLookbackWindowTests {

        @Test
        @DisplayName("1-level lookback recovery: skipped parenthesis before square bracket")
        void testLookbackOneLevel() {
            // [ ( ] -> '(' was skipped before ']'
            String code = "int[] arr = new int[ (1 + 2 ];";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diag.code());
            assertEquals(22, diag.location().column()); // '(' at index 21 -> col 22
            assertTrue(diag.message().contains("Unclosed delimiter '('"));
            assertTrue(diag.message().contains("expected matching ')' before ']'"));
            assertEquals("Insert matching ')'", diag.suggestedFix().get());
        }

        @Test
        @DisplayName("2-level lookback recovery: skipped { and [ before }")
        void testLookbackTwoLevels() {
            // Synthesized tokens: { [ ( }
            SourceLocation l1 = SourceLocation.of(1, 1, 0);
            SourceLocation l2 = SourceLocation.of(1, 3, 2);
            SourceLocation l3 = SourceLocation.of(1, 5, 4);
            SourceLocation l4 = SourceLocation.of(1, 7, 6);

            List<Token> tokens = List.of(
                Token.of(TokenType.DELIMITER, "{", SourceSpan.point(l1)),
                Token.of(TokenType.DELIMITER, "[", SourceSpan.point(l2)),
                Token.of(TokenType.DELIMITER, "(", SourceSpan.point(l3)),
                Token.of(TokenType.DELIMITER, "}", SourceSpan.point(l4))
            );

            SourcePayload payload = SourcePayload.singleLine("{ [ ( }", Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokens);

            assertEquals(2, diagnostics.size());
            // Both '[' and '(' reported as unclosed
            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(0).code());
            assertEquals(l2, diagnostics.get(0).location());
            assertEquals("Insert matching ']'", diagnostics.get(0).suggestedFix().get());

            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(1).code());
            assertEquals(l3, diagnostics.get(1).location());
            assertEquals("Insert matching ')'", diagnostics.get(1).suggestedFix().get());
        }

        @Test
        @DisplayName("3-level lookback recovery: skipped { ( [ ( }")
        void testLookbackThreeLevels() {
            // Synthesized tokens: { ( [ ( }
            SourceLocation l1 = SourceLocation.of(1, 1, 0);
            SourceLocation l2 = SourceLocation.of(1, 3, 2);
            SourceLocation l3 = SourceLocation.of(1, 5, 4);
            SourceLocation l4 = SourceLocation.of(1, 7, 6);
            SourceLocation l5 = SourceLocation.of(1, 9, 8);

            List<Token> tokens = List.of(
                Token.of(TokenType.DELIMITER, "{", SourceSpan.point(l1)),
                Token.of(TokenType.DELIMITER, "(", SourceSpan.point(l2)),
                Token.of(TokenType.DELIMITER, "[", SourceSpan.point(l3)),
                Token.of(TokenType.DELIMITER, "(", SourceSpan.point(l4)),
                Token.of(TokenType.DELIMITER, "}", SourceSpan.point(l5))
            );

            SourcePayload payload = SourcePayload.singleLine("{ ( [ ( }", Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokens);

            assertEquals(3, diagnostics.size());
            assertEquals(l2, diagnostics.get(0).location());
            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(0).code());
            assertEquals(l3, diagnostics.get(1).location());
            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(1).code());
            assertEquals(l4, diagnostics.get(2).location());
            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(2).code());
        }

        @Test
        @DisplayName("Exceeding lookback window (> 3 levels) does not skip past 3 frames")
        void testLookbackExceedingThreeLevels() {
            // Stack depth 5: { ( ( ( ( }
            // Lookback from top (level 0) checks levels 1, 2, 3 (which are all '(').
            // Level 4 is '{', which exceeds the 3-frame lookback window.
            // Hence treated as mismatched with top '('.
            SourceLocation l1 = SourceLocation.of(1, 1, 0);
            SourceLocation l2 = SourceLocation.of(1, 3, 2);
            SourceLocation l3 = SourceLocation.of(1, 5, 4);
            SourceLocation l4 = SourceLocation.of(1, 7, 6);
            SourceLocation l5 = SourceLocation.of(1, 9, 8);
            SourceLocation l6 = SourceLocation.of(1, 11, 10);

            List<Token> tokens = List.of(
                Token.of(TokenType.DELIMITER, "{", SourceSpan.point(l1)),
                Token.of(TokenType.DELIMITER, "(", SourceSpan.point(l2)),
                Token.of(TokenType.DELIMITER, "(", SourceSpan.point(l3)),
                Token.of(TokenType.DELIMITER, "(", SourceSpan.point(l4)),
                Token.of(TokenType.DELIMITER, "(", SourceSpan.point(l5)),
                Token.of(TokenType.DELIMITER, "}", SourceSpan.point(l6))
            );

            SourcePayload payload = SourcePayload.singleLine("{ ( ( ( ( }", Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokens);

            // Diagnostics: 1 mismatch for '}' with top '(', plus 4 unclosed at EOF ({ and remaining three '(')
            assertEquals(5, diagnostics.size());
            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diagnostics.get(0).code());
            assertEquals(l6, diagnostics.get(0).location());
            assertTrue(diagnostics.get(0).message().contains("expected ')' to match '('"));

            // Remaining 4 on stack at EOF
            for (int i = 1; i <= 4; i++) {
                assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(i).code());
            }
        }
    }

    @Nested
    @DisplayName("Multiple Errors in One File with Clean Recovery")
    class MultipleErrorsAndCleanRecoveryTests {

        @Test
        @DisplayName("Multiple errors in Java: mismatch, unexpected closing, lookback recovery, and unclosed at EOF")
        void testMultipleErrorsJava() {
            String code = """
                int a = (1 + 2];
                int b = foo() );
                int[] c = new int[ (1 + 2 ];
                int[] d = new int[ 10;
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            // Expected 4 errors:
            // 1. Line 1: Mismatch: (1 + 2] -> ERR_MISMATCHED_DELIMITER
            // 2. Line 2: Unexpected closing: foo() ) -> ERR_UNEXPECTED_CLOSING_DELIMITER
            // 3. Line 3: Skipped closure: '(' unclosed before ']' -> ERR_UNCLOSED_DELIMITER
            // 4. Line 4: Unclosed '[' at EOF -> ERR_UNCLOSED_DELIMITER
            assertEquals(4, diagnostics.size());

            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diagnostics.get(0).code());
            assertEquals(1, diagnostics.get(0).location().line());

            assertEquals(DelimiterMatchingChecker.ERR_UNEXPECTED_CLOSING_DELIMITER, diagnostics.get(1).code());
            assertEquals(2, diagnostics.get(1).location().line());

            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(2).code());
            assertEquals(3, diagnostics.get(2).location().line());

            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(3).code());
            assertEquals(4, diagnostics.get(3).location().line());
        }

        @Test
        @DisplayName("Java class structure with internal errors recovers without corrupting outer scopes")
        void testJavaClassScopeRecovery() {
            String code = """
                class Test {
                    void m1() {
                        int a = (1 + 2];
                    }
                    void m2() {
                        int[] b = new int[ (1 + 2 ];
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(2, diagnostics.size());
            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diagnostics.get(0).code());
            assertEquals(3, diagnostics.get(0).location().line());

            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(1).code());
            assertEquals(6, diagnostics.get(1).location().line());
        }

        @Test
        @DisplayName("Multiple errors in Python: mismatch, unexpected closing, lookback recovery, and unclosed at EOF")
        void testMultipleErrorsPython() {
            String code = """
                a = (1 + 2]
                b = foo() )
                c = [(1 + 2]
                d = [1, 2, 3
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            // Line 1: mismatch (] -> ERR_MISMATCHED_DELIMITER
            // Line 2: unexpected closing ')' -> ERR_UNEXPECTED_CLOSING_DELIMITER
            // Line 3: lookback recovery: '(' unclosed before ']' -> ERR_UNCLOSED_DELIMITER
            // Line 4: '[' unclosed at EOF -> ERR_UNCLOSED_DELIMITER
            assertEquals(4, diagnostics.size());

            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diagnostics.get(0).code());
            assertEquals(1, diagnostics.get(0).location().line());

            assertEquals(DelimiterMatchingChecker.ERR_UNEXPECTED_CLOSING_DELIMITER, diagnostics.get(1).code());
            assertEquals(2, diagnostics.get(1).location().line());

            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(2).code());
            assertEquals(3, diagnostics.get(2).location().line());

            assertEquals(DelimiterMatchingChecker.ERR_UNCLOSED_DELIMITER, diagnostics.get(3).code());
            assertEquals(4, diagnostics.get(3).location().line());
        }

        @Test
        @DisplayName("Multiple errors in C++: sequential mismatches recover cleanly")
        void testMultipleErrorsCpp() {
            String code = """
                int a = (1 + 2];
                int b = {3 + 4];
                int c = [5 + 6);
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(3, diagnostics.size());
            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diagnostics.get(0).code());
            assertEquals(1, diagnostics.get(0).location().line());

            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diagnostics.get(1).code());
            assertEquals(2, diagnostics.get(1).location().line());

            assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diagnostics.get(2).code());
            assertEquals(3, diagnostics.get(2).location().line());
        }
    }
}
