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

@DisplayName("IdentifierSyntaxChecker Tests (Issue #17 / ADR 0008)")
class IdentifierSyntaxCheckerTest {

    private final IdentifierSyntaxChecker checker = new IdentifierSyntaxChecker();

    private List<Token> tokenize(SourcePayload payload) {
        return Tokenizer.forLanguage(payload.language()).tokenize(payload);
    }

    @Nested
    @DisplayName("Contract and Invariant Tests")
    class ContractAndInvariantTests {

        @Test
        @DisplayName("Returns IDENTIFIER_NAMING check category")
        void testCategory() {
            assertEquals(CheckCategory.IDENTIFIER_NAMING, checker.category());
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
    }

    @Nested
    @DisplayName("Valid Identifiers Tests")
    class ValidIdentifiersTests {

        @Test
        @DisplayName("Accepts valid Java identifiers including $, _, and unicode")
        void testValidJavaIdentifiers() {
            String code = """
                public class ValidTest {
                    int x = 1;
                    int $myVar = 2;
                    int _count = 3;
                    int foo$bar = 4;
                    int café = 5;
                    int тест = 6;
                    public static void main(String[] args) {}
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), "Expected no diagnostics for valid Java identifiers, but found: " + diagnostics);
        }

        @Test
        @DisplayName("Accepts valid Python identifiers including _, unicode, and letters")
        void testValidPythonIdentifiers() {
            String code = """
                x = 1
                _count = 2
                my_var_123 = 3
                café = 4
                def test_func():
                    pass
                class MyClass:
                    pass
                import math as m
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), "Expected no diagnostics for valid Python identifiers, but found: " + diagnostics);
        }

        @Test
        @DisplayName("Accepts valid C++ identifiers including _, compound types, and scoped enums")
        void testValidCppIdentifiers() {
            String code = """
                int x = 1;
                int _count = 2;
                int my_var_123 = 3;
                long long big_number = 100;
                unsigned int positive = 5;
                enum class Color { RED, GREEN };
                class Foo {};
                struct Bar {};
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), "Expected no diagnostics for valid C++ identifiers, but found: " + diagnostics);
        }
    }

    @Nested
    @DisplayName("Starts With Digit Tests")
    class StartsWithDigitTests {

        @Test
        @DisplayName("Detects 123var, 0abc, 1_foo in Java")
        void testJavaStartsWithDigit() {
            assertStartsWithDigit("123var", Language.JAVA);
            assertStartsWithDigit("0abc", Language.JAVA);
            assertStartsWithDigit("1_foo", Language.JAVA);
        }

        @Test
        @DisplayName("Detects 123var, 0abc, 1_foo in Python")
        void testPythonStartsWithDigit() {
            assertStartsWithDigit("123var", Language.PYTHON);
            assertStartsWithDigit("0abc", Language.PYTHON);
            assertStartsWithDigit("1_foo", Language.PYTHON);
        }

        @Test
        @DisplayName("Detects 123var, 0abc, 1_foo in C++")
        void testCppStartsWithDigit() {
            assertStartsWithDigit("123var", Language.CPP);
            assertStartsWithDigit("0abc", Language.CPP);
            assertStartsWithDigit("1_foo", Language.CPP);
        }

        @Test
        @DisplayName("Does not flag numbers separated from identifiers by whitespace")
        void testSeparatedByWhitespace() {
            for (Language lang : Language.values()) {
                SourcePayload payload = SourcePayload.snippet("123 var 0 abc", lang);
                List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
                assertTrue(diagnostics.isEmpty(), "Expected no diagnostics for whitespace-separated tokens in " + lang);
            }
        }

        private void assertStartsWithDigit(String snippet, Language language) {
            SourcePayload payload = SourcePayload.snippet(snippet, language);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertFalse(diagnostics.isEmpty(), "Expected diagnostic for: " + snippet + " in " + language);
            Diagnostic diag = diagnostics.get(0);
            assertEquals(IdentifierSyntaxChecker.ERR_IDENTIFIER_STARTS_WITH_DIGIT, diag.code());
            assertEquals(CheckCategory.IDENTIFIER_NAMING, diag.category());
            assertEquals(Severity.ERROR, diag.severity());
            assertEquals(SourceLocation.start(), diag.location());
            assertTrue(diag.suggestedFix().isPresent());
            assertEquals("Prefix identifier with a letter or underscore, or separate number from identifier", diag.suggestedFix().get());
        }
    }

    @Nested
    @DisplayName("Illegal Characters Tests")
    class IllegalCharactersTests {

        @Test
        @DisplayName("Flags $ in Python identifiers")
        void testDollarInPython() {
            SourcePayload payload = SourcePayload.snippet("$var = 1", Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertFalse(diagnostics.isEmpty());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, diag.code());
            assertEquals(CheckCategory.IDENTIFIER_NAMING, diag.category());
            assertEquals(Severity.ERROR, diag.severity());

            SourcePayload middlePayload = SourcePayload.snippet("foo$bar = 2", Language.PYTHON);
            List<Diagnostic> middleDiags = checker.check(middlePayload, tokenize(middlePayload));
            assertFalse(middleDiags.isEmpty());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, middleDiags.get(0).code());
        }

        @Test
        @DisplayName("Flags $ in C++ identifiers")
        void testDollarInCpp() {
            SourcePayload payload = SourcePayload.snippet("int $var = 1;", Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertFalse(diagnostics.isEmpty());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, diag.code());
            assertEquals(CheckCategory.IDENTIFIER_NAMING, diag.category());

            SourcePayload middlePayload = SourcePayload.snippet("int foo$bar = 2;", Language.CPP);
            List<Diagnostic> middleDiags = checker.check(middlePayload, tokenize(middlePayload));
            assertFalse(middleDiags.isEmpty());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, middleDiags.get(0).code());
        }

        @Test
        @DisplayName("Does not flag $ in Java identifiers")
        void testDollarAllowedInJava() {
            SourcePayload p1 = SourcePayload.snippet("int $var = 1;", Language.JAVA);
            assertTrue(checker.check(p1, tokenize(p1)).isEmpty());

            SourcePayload p2 = SourcePayload.snippet("int foo$bar = 2;", Language.JAVA);
            assertTrue(checker.check(p2, tokenize(p2)).isEmpty());
        }

        @Test
        @DisplayName("Flags abutting unknown characters like @var in C++ and var#name in Java/C++")
        void testAbuttingUnknownCharacters() {
            // @var in C++
            SourcePayload cppPayload = SourcePayload.snippet("@var = 1;", Language.CPP);
            List<Diagnostic> cppDiags = checker.check(cppPayload, tokenize(cppPayload));
            assertFalse(cppDiags.isEmpty());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, cppDiags.get(0).code());

            // var#name in Java
            SourcePayload javaPayload = SourcePayload.snippet("int var#name = 1;", Language.JAVA);
            List<Diagnostic> javaDiags = checker.check(javaPayload, tokenize(javaPayload));
            assertFalse(javaDiags.isEmpty());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, javaDiags.get(0).code());

            // var#name in C++
            SourcePayload cppHashPayload = SourcePayload.snippet("int var#name = 1;", Language.CPP);
            List<Diagnostic> cppHashDiags = checker.check(cppHashPayload, tokenize(cppHashPayload));
            assertFalse(cppHashDiags.isEmpty());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, cppHashDiags.get(0).code());
        }

        @Test
        @DisplayName("Flags mock UNKNOWN token directly abutting identifier")
        void testMockUnknownTokenAbutting() {
            SourcePayload payload = SourcePayload.snippet("~var", Language.JAVA);
            SourceLocation loc1 = SourceLocation.of(1, 1, 0);
            SourceLocation loc2 = SourceLocation.of(1, 2, 1);
            SourceLocation loc3 = SourceLocation.of(1, 5, 4);

            Token unknownTok = Token.of(TokenType.UNKNOWN, "~", SourceSpan.of(loc1, loc2));
            Token idTok = Token.of(TokenType.IDENTIFIER, "var", SourceSpan.of(loc2, loc3));

            List<Diagnostic> diags = checker.check(payload, List.of(unknownTok, idTok));
            assertEquals(1, diags.size());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, diags.get(0).code());
            assertEquals(loc1, diags.get(0).location());
        }

        @Test
        @DisplayName("Flags mock IDENTIFIER token containing $ in Python and C++")
        void testMockIdentifierContainingDollar() {
            SourceLocation loc1 = SourceLocation.of(1, 1, 0);
            SourceLocation loc2 = SourceLocation.of(1, 8, 7);
            Token mockToken = Token.of(TokenType.IDENTIFIER, "bad$var", SourceSpan.of(loc1, loc2));

            SourcePayload pyPayload = SourcePayload.snippet("bad$var", Language.PYTHON);
            List<Diagnostic> pyDiags = checker.check(pyPayload, List.of(mockToken));
            assertEquals(1, pyDiags.size());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, pyDiags.get(0).code());

            SourcePayload cppPayload = SourcePayload.snippet("bad$var", Language.CPP);
            List<Diagnostic> cppDiags = checker.check(cppPayload, List.of(mockToken));
            assertEquals(1, cppDiags.size());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, cppDiags.get(0).code());
        }
    }

    @Nested
    @DisplayName("Keyword As Identifier Tests")
    class KeywordAsIdentifierTests {

        @Test
        @DisplayName("Detects int class = 1; in Java and C++")
        void testIntClass() {
            SourcePayload javaPayload = SourcePayload.snippet("int class = 1;", Language.JAVA);
            List<Diagnostic> javaDiags = checker.check(javaPayload, tokenize(javaPayload));
            assertEquals(1, javaDiags.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, javaDiags.get(0).code());

            SourcePayload cppPayload = SourcePayload.snippet("int class = 1;", Language.CPP);
            List<Diagnostic> cppDiags = checker.check(cppPayload, tokenize(cppPayload));
            assertEquals(1, cppDiags.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, cppDiags.get(0).code());
        }

        @Test
        @DisplayName("Detects void return() in Java and C++")
        void testVoidReturn() {
            SourcePayload javaPayload = SourcePayload.snippet("void return() {}", Language.JAVA);
            List<Diagnostic> javaDiags = checker.check(javaPayload, tokenize(javaPayload));
            assertEquals(1, javaDiags.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, javaDiags.get(0).code());

            SourcePayload cppPayload = SourcePayload.snippet("void return();", Language.CPP);
            List<Diagnostic> cppDiags = checker.check(cppPayload, tokenize(cppPayload));
            assertEquals(1, cppDiags.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, cppDiags.get(0).code());
        }

        @Test
        @DisplayName("Detects declaration keywords followed by keywords in Java and C++")
        void testDeclarationKeywordsFollowedByKeywords() {
            SourcePayload classInt = SourcePayload.snippet("class int {}", Language.JAVA);
            List<Diagnostic> diags1 = checker.check(classInt, tokenize(classInt));
            assertEquals(1, diags1.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, diags1.get(0).code());

            SourcePayload structReturn = SourcePayload.snippet("struct return {};", Language.CPP);
            List<Diagnostic> diags2 = checker.check(structReturn, tokenize(structReturn));
            assertEquals(1, diags2.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, diags2.get(0).code());

            SourcePayload nsInt = SourcePayload.snippet("namespace int {}", Language.CPP);
            List<Diagnostic> diags3 = checker.check(nsInt, tokenize(nsInt));
            assertEquals(1, diags3.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, diags3.get(0).code());
        }

        @Test
        @DisplayName("Detects def for():, class while:, and import math as if in Python")
        void testPythonKeywordsAsIdentifiers() {
            SourcePayload defFor = SourcePayload.snippet("def for(): pass", Language.PYTHON);
            List<Diagnostic> d1 = checker.check(defFor, tokenize(defFor));
            assertEquals(1, d1.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, d1.get(0).code());

            SourcePayload classWhile = SourcePayload.snippet("class while: pass", Language.PYTHON);
            List<Diagnostic> d2 = checker.check(classWhile, tokenize(classWhile));
            assertEquals(1, d2.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, d2.get(0).code());

            SourcePayload asIf = SourcePayload.snippet("import math as if", Language.PYTHON);
            List<Diagnostic> d3 = checker.check(asIf, tokenize(asIf));
            assertEquals(1, d3.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, d3.get(0).code());
        }
    }

    @Nested
    @DisplayName("Multiple Errors And Clean Recovery Tests")
    class MultipleErrorsAndCleanRecoveryTests {

        @Test
        @DisplayName("Handles multiple sequential identifier errors in Java without cascading")
        void testJavaMultipleErrors() {
            String code = """
                int class = 1;
                void return();
                123var = 456;
                int foo#bar = 789;
                int validVar = 100;
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(4, diagnostics.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, diagnostics.get(0).code());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, diagnostics.get(1).code());
            assertEquals(IdentifierSyntaxChecker.ERR_IDENTIFIER_STARTS_WITH_DIGIT, diagnostics.get(2).code());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, diagnostics.get(3).code());
        }

        @Test
        @DisplayName("Handles multiple sequential identifier errors in Python without cascading")
        void testPythonMultipleErrors() {
            String code = """
                def for():
                    pass

                class while:
                    pass

                123var = 1
                $bad = 2
                valid_ident = 3
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(4, diagnostics.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, diagnostics.get(0).code());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, diagnostics.get(1).code());
            assertEquals(IdentifierSyntaxChecker.ERR_IDENTIFIER_STARTS_WITH_DIGIT, diagnostics.get(2).code());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, diagnostics.get(3).code());
        }

        @Test
        @DisplayName("Handles multiple sequential identifier errors in C++ without cascading")
        void testCppMultipleErrors() {
            String code = """
                struct return {};
                int class = 1;
                0abc = 2;
                @var = 3;
                int valid_ident = 4;
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(4, diagnostics.size());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, diagnostics.get(0).code());
            assertEquals(IdentifierSyntaxChecker.ERR_KEYWORD_AS_IDENTIFIER, diagnostics.get(1).code());
            assertEquals(IdentifierSyntaxChecker.ERR_IDENTIFIER_STARTS_WITH_DIGIT, diagnostics.get(2).code());
            assertEquals(IdentifierSyntaxChecker.ERR_ILLEGAL_IDENTIFIER_CHAR, diagnostics.get(3).code());
        }
    }
}
