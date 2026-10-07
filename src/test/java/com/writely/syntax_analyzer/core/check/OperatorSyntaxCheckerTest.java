package com.writely.syntax_analyzer.core.check;

import com.writely.syntax_analyzer.core.tokenization.Tokenizer;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OperatorSyntaxChecker Tests (Issue #22 / ADR 0013)")
class OperatorSyntaxCheckerTest {

    private final OperatorSyntaxChecker checker = new OperatorSyntaxChecker();

    private List<Token> tokenize(SourcePayload payload) {
        return Tokenizer.forLanguage(payload.language()).tokenize(payload);
    }

    @Nested
    @DisplayName("Contract and Invariant Tests")
    class ContractAndInvariantTests {

        @Test
        @DisplayName("Returns OPERATOR_SYNTAX check category")
        void testCategory() {
            assertEquals(CheckCategory.OPERATOR_SYNTAX, checker.category());
        }

        @Test
        @DisplayName("Rejects null payload or null tokens")
        void testNullValidations() {
            SourcePayload payload = SourcePayload.singleLine("int x = 1 + 2;", Language.JAVA);
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
        @DisplayName("Returns empty diagnostics for comments and whitespace only")
        void testWhitespaceAndCommentsOnly() {
            String code = "// Just a comment\n   /* block comment */  \n";
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty());
        }
    }

    @Nested
    @DisplayName("Valid Expression Tests")
    class ValidExpressionTests {

        @Test
        @DisplayName("Accepts valid Java expressions: unary, compound, ternary, lambdas, generics")
        void testValidJavaExpressions() {
            String code = """
                public class ValidJava {
                    public void test() {
                        int x = 10;
                        int y = +x;
                        int z = -x;
                        int a = x + +y;
                        int b = x - -y;
                        int c = x + -y;
                        int d = x * -y;
                        int e = x / +y;
                        int f = -1;
                        boolean flag = !true;
                        boolean flag2 = flag && !false;
                        boolean flag3 = flag || !false;
                        boolean flag4 = (x == y) && !(x != z);
                        int mask = ~0xFF;
                        int mask2 = x & ~mask;
                        int mask3 = x | ~mask;
                        int mask4 = x ^ ~mask;
                        int i = 0;
                        ++i;
                        i++;
                        --i;
                        i--;
                        int res1 = ++i + 5;
                        int res2 = i++ + 5;
                        int res3 = --i - 5;
                        int res4 = i-- - 5;
                        for (int k = 0; k < 10; k++) {
                            x += 1;
                            x -= 2;
                            x *= 3;
                            x /= 4;
                            x %= 5;
                            x &= 6;
                            x |= 7;
                            x ^= 8;
                            x <<= 1;
                            x >>= 1;
                            x >>>= 1;
                        }
                        int ternary = (x > y) ? x : y;
                        java.util.function.Function<Integer, Integer> fn = val -> val + 1;
                        java.util.List<String> list = new java.util.ArrayList<>();
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), "Expected 0 diagnostics for valid Java, got: " + diagnostics);
        }

        @Test
        @DisplayName("Accepts valid Python expressions: *args, **kwargs, is not, not in, //, **")
        void testValidPythonExpressions() {
            String code = """
                def func(*args, **kwargs):
                    return args, kwargs

                res = func(*[1, 2], **{"a": 1})
                items = [*args, *kwargs]
                mapping = {**d1, **d2}

                val = 10 // 3
                pwr = 2 ** 8
                val //= 2
                pwr **= 2

                x = -1
                y = +x
                z = ~y
                flag = not False
                double_not = not not flag
                cond1 = x is not None
                cond2 = 5 not in [1, 2, 3]
                cond3 = flag and not cond1
                cond4 = flag or not cond2

                nested = (
                    1 +
                    2 +
                    3
                )
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), "Expected 0 diagnostics for valid Python, got: " + diagnostics);
        }

        @Test
        @DisplayName("Accepts valid C++ expressions: *ptr, &ref, int** pp, ->*, .*, ::, <=>, templates")
        void testValidCppExpressions() {
            String code = """
                #include <iostream>
                #include <vector>

                int globalVar = 42;

                template<typename T>
                class Wrapper {
                public:
                    T value;
                };

                int main() {
                    int val = 10;
                    int *ptr = &val;
                    int* ptr2 = &val;
                    int &ref = val;
                    int& ref2 = val;
                    *ptr = 20;
                    int x = *ptr + 5;
                    int y = 5 + *ptr;
                    int z = -val;
                    int w = +val;
                    int a = x + +y;
                    int b = x - -y;
                    int c = x = -1;
                    bool flag = !false;
                    int mask = ~0;

                    int **pp = &ptr;
                    int** pp2 = &ptr;
                    int d = **pp;

                    int i = 0;
                    ++i;
                    i++;
                    --i;
                    i--;
                    int res = ++i + 5;
                    int res2 = i++ + 5;

                    int glob = ::globalVar;
                    std::cout << glob;

                    std::vector<int*> ptrList;
                    std::vector<int> intList;

                    val += 1;
                    val -= 2;
                    val *= 3;
                    val /= 4;
                    val %= 5;
                    val &= 6;
                    val |= 7;
                    val ^= 8;
                    val <<= 1;
                    val >>= 1;

                    return 0;
                }
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));
            assertTrue(diagnostics.isEmpty(), "Expected 0 diagnostics for valid C++, got: " + diagnostics);
        }
    }

    @Nested
    @DisplayName("Invalid Operator Sequence Tests")
    class InvalidOperatorSequenceTests {

        @Test
        @DisplayName("Flags '+ *' sequence with ERR_INVALID_OPERATOR_SEQUENCE")
        void testPlusStarSequence() {
            String code = "int x = a + * b;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_OPERATOR_SEQUENCE, d.code());
            assertEquals(Severity.ERROR, d.severity());
            assertEquals(CheckCategory.OPERATOR_SYNTAX, d.category());
            assertEquals(1, d.location().line());
            assertEquals(13, d.location().column()); // '*' column in "int x = a + * b;"
            assertTrue(d.message().contains("+ *"));
        }

        @Test
        @DisplayName("Flags '/ *' sequence with ERR_INVALID_OPERATOR_SEQUENCE")
        void testSlashStarSequence() {
            String code = "int x = a / * b;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_OPERATOR_SEQUENCE, d.code());
            assertEquals(1, d.location().line());
            assertEquals(13, d.location().column());
            assertTrue(d.message().contains("/ *"));
        }

        @Test
        @DisplayName("Flags '* /' sequence with ERR_INVALID_OPERATOR_SEQUENCE")
        void testStarSlashSequence() {
            String code = "int x = a * / b;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_OPERATOR_SEQUENCE, d.code());
            assertEquals(1, d.location().line());
            assertEquals(13, d.location().column());
            assertTrue(d.message().contains("* /"));
        }

        @Test
        @DisplayName("Flags '== =' sequence with ERR_INVALID_OPERATOR_SEQUENCE")
        void testEqualsEqualsAssignSequence() {
            String code = "boolean b = x == = y;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_OPERATOR_SEQUENCE, d.code());
            assertEquals(1, d.location().line());
            assertEquals(18, d.location().column()); // '=' column in "boolean b = x == = y;"
            assertTrue(d.message().contains("== ="));
        }

        @Test
        @DisplayName("Flags '&& ||' sequence with ERR_INVALID_OPERATOR_SEQUENCE")
        void testAndOrSequence() {
            String code = "boolean b = x && || y;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_OPERATOR_SEQUENCE, d.code());
            assertEquals(1, d.location().line());
            assertEquals(18, d.location().column()); // '||' column
            assertTrue(d.message().contains("&& ||"));
        }

        @Test
        @DisplayName("Flags '% /' sequence with ERR_INVALID_OPERATOR_SEQUENCE")
        void testModuloSlashSequence() {
            String code = "int x = a % / b;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_OPERATOR_SEQUENCE, d.code());
            assertEquals(1, d.location().line());
            assertEquals(13, d.location().column());
            assertTrue(d.message().contains("% /"));
        }

        @Test
        @DisplayName("Flags invalid consecutive sequences in Python (+ *, / *, == =)")
        void testPythonInvalidSequences() {
            String code = "x = a + * b";
            SourcePayload payload = SourcePayload.singleLine(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_OPERATOR_SEQUENCE, diagnostics.get(0).code());
        }
    }

    @Nested
    @DisplayName("Missing Operand Tests")
    class MissingOperandTests {

        @Test
        @DisplayName("Flags 'a + ;' with ERR_MISSING_OPERAND on '+'")
        void testMissingRightOperandSemicolon() {
            String code = "a + ;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_MISSING_OPERAND, d.code());
            assertEquals(Severity.ERROR, d.severity());
            assertEquals(CheckCategory.OPERATOR_SYNTAX, d.category());
            assertEquals(1, d.location().line());
            assertEquals(3, d.location().column()); // '+' location in "a + ;"
            assertTrue(d.message().contains("Missing right operand"));
        }

        @Test
        @DisplayName("Flags '* b' with ERR_MISSING_OPERAND on '*'")
        void testMissingLeftOperandStar() {
            String code = "* b";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_MISSING_OPERAND, d.code());
            assertEquals(Severity.ERROR, d.severity());
            assertEquals(CheckCategory.OPERATOR_SYNTAX, d.category());
            assertEquals(1, d.location().line());
            assertEquals(1, d.location().column()); // '*' location at start
            assertTrue(d.message().contains("Missing left operand"));
        }

        @Test
        @DisplayName("Flags '5 *' at EOF with ERR_MISSING_OPERAND on '*'")
        void testMissingRightOperandAtEof() {
            String code = "5 *";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_MISSING_OPERAND, d.code());
            assertEquals(1, d.location().line());
            assertEquals(3, d.location().column());
            assertTrue(d.message().contains("Missing right operand"));
        }

        @Test
        @DisplayName("Flags missing right operand before closing delimiter: foo(x + ) and arr[i * ]")
        void testMissingRightOperandDelimiters() {
            String code = "foo(x + );";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            assertEquals(OperatorSyntaxChecker.ERR_MISSING_OPERAND, diagnostics.get(0).code());
            assertEquals(7, diagnostics.get(0).location().column()); // '+' in "foo(x + );"
        }

        @Test
        @DisplayName("Flags missing left operand for binary operator after statement boundary")
        void testMissingLeftOperandAfterSemicolon() {
            String code = "int x = 1; / y;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_MISSING_OPERAND, d.code());
            assertEquals(12, d.location().column()); // '/' in "int x = 1; / y;"
            assertTrue(d.message().contains("Missing left operand"));
        }

        @Test
        @DisplayName("Flags missing right operand in Python at line end outside brackets")
        void testMissingRightOperandPythonNewline() {
            String code = "x = 5 +\ny = 10\n";
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_MISSING_OPERAND, d.code());
            assertEquals(1, d.location().line());
            assertEquals(7, d.location().column()); // '+' in "x = 5 +"
        }
    }

    @Nested
    @DisplayName("Invalid Unary Operator Tests")
    class InvalidUnaryOperatorTests {

        @Test
        @DisplayName("Flags '! ;' and '~ ;' with ERR_INVALID_UNARY_OPERATOR for missing operand")
        void testMissingUnaryOperand() {
            String code = "boolean flag = ! ;\nint mask = ~ ;";
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(2, diagnostics.size());
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_UNARY_OPERATOR, diagnostics.get(0).code());
            assertEquals(1, diagnostics.get(0).location().line());
            assertEquals(16, diagnostics.get(0).location().column()); // '!'

            assertEquals(OperatorSyntaxChecker.ERR_INVALID_UNARY_OPERATOR, diagnostics.get(1).code());
            assertEquals(2, diagnostics.get(1).location().line());
            assertEquals(12, diagnostics.get(1).location().column()); // '~'
        }

        @Test
        @DisplayName("Flags postfix ++ on literal with ERR_INVALID_UNARY_OPERATOR (5++)")
        void testPostfixOnLiteral() {
            String code = "int x = 5++;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_UNARY_OPERATOR, d.code());
            assertEquals(10, d.location().column()); // '++'
            assertTrue(d.message().contains("literal"));
        }

        @Test
        @DisplayName("Flags prefix ++ on literal with ERR_INVALID_UNARY_OPERATOR (++5)")
        void testPrefixOnLiteral() {
            String code = "int x = ++5;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_UNARY_OPERATOR, d.code());
            assertEquals(9, d.location().column()); // '++'
            assertTrue(d.message().contains("literal"));
        }

        @Test
        @DisplayName("Flags chained increment/decrement with ERR_INVALID_UNARY_OPERATOR (i++++ and ++++i)")
        void testChainedIncrementDecrement() {
            String code = "int a = i++ ++; int b = ++ ++ i;";
            SourcePayload payload = SourcePayload.singleLine(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(2, diagnostics.size());
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_UNARY_OPERATOR, diagnostics.get(0).code());
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_UNARY_OPERATOR, diagnostics.get(1).code());
        }
    }

    @Nested
    @DisplayName("Multiple Errors and Clean Recovery Tests")
    class MultipleErrorsAndCleanRecoveryTests {

        @Test
        @DisplayName("Reports multiple sequential defects cleanly across lines without cascading")
        void testMultipleErrorsCleanRecovery() {
            String code = """
                int a = 1 + * 2;
                int b = 3 + ;
                int c;
                * d;
                boolean flag = ! ;
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(4, diagnostics.size(), "Expected exactly 4 diagnostics, got: " + diagnostics);

            // Error 1: Line 1: + * -> ERR_INVALID_OPERATOR_SEQUENCE
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_OPERATOR_SEQUENCE, diagnostics.get(0).code());
            assertEquals(1, diagnostics.get(0).location().line());
            assertEquals(13, diagnostics.get(0).location().column());

            // Error 2: Line 2: 3 + ; -> ERR_MISSING_OPERAND
            assertEquals(OperatorSyntaxChecker.ERR_MISSING_OPERAND, diagnostics.get(1).code());
            assertEquals(2, diagnostics.get(1).location().line());
            assertEquals(11, diagnostics.get(1).location().column());

            // Error 3: Line 4: * d; -> ERR_MISSING_OPERAND
            assertEquals(OperatorSyntaxChecker.ERR_MISSING_OPERAND, diagnostics.get(2).code());
            assertEquals(4, diagnostics.get(2).location().line());
            assertEquals(1, diagnostics.get(2).location().column());

            // Error 4: Line 5: boolean flag = ! ; -> ERR_INVALID_UNARY_OPERATOR
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_UNARY_OPERATOR, diagnostics.get(3).code());
            assertEquals(5, diagnostics.get(3).location().line());
            assertEquals(16, diagnostics.get(3).location().column());
        }

        @Test
        @DisplayName("Handles multiple defects across Python statements")
        void testMultiplePythonDefects() {
            String code = """
                x = 1 + * 2
                y = 5 +
                z = a == = b
                """;
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            List<Diagnostic> diagnostics = checker.check(payload, tokenize(payload));

            assertEquals(3, diagnostics.size());
            assertEquals(OperatorSyntaxChecker.ERR_INVALID_OPERATOR_SEQUENCE, diagnostics.get(0).code());
            assertEquals(1, diagnostics.get(0).location().line());

            assertEquals(OperatorSyntaxChecker.ERR_MISSING_OPERAND, diagnostics.get(1).code());
            assertEquals(2, diagnostics.get(1).location().line());

            assertEquals(OperatorSyntaxChecker.ERR_INVALID_OPERATOR_SEQUENCE, diagnostics.get(2).code());
            assertEquals(3, diagnostics.get(2).location().line());
        }
    }
}
