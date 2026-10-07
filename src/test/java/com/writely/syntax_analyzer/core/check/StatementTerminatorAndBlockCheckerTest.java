package com.writely.syntax_analyzer.core.check;

import com.writely.syntax_analyzer.core.tokenization.Tokenizer;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StatementTerminatorAndBlockChecker Tests (Issue #21 / ADR 0012)")
class StatementTerminatorAndBlockCheckerTest {

    private final StatementTerminatorAndBlockChecker checker = new StatementTerminatorAndBlockChecker();

    private List<Token> tokenize(SourcePayload payload) {
        return Tokenizer.forLanguage(payload.language()).tokenize(payload);
    }

    private List<Diagnostic> check(String code, Language language) {
        SourcePayload payload = SourcePayload.snippet(code, language);
        return checker.check(payload, tokenize(payload));
    }

    // =========================================================================
    // 1. Contract and Invariant Tests
    // =========================================================================

    @Nested
    @DisplayName("Contract and Invariant Tests")
    class ContractAndInvariantTests {

        @Test
        @DisplayName("Returns STATEMENT_TERMINATOR check category")
        void testCategory() {
            assertEquals(CheckCategory.STATEMENT_TERMINATOR, checker.category());
        }

        @Test
        @DisplayName("Rejects null payload or null tokens with NullPointerException")
        void testNullValidations() {
            SourcePayload payload = SourcePayload.snippet("int x = 10;", Language.JAVA);
            List<Token> tokens = List.of();

            assertThrows(NullPointerException.class, () -> checker.check(null, tokens));
            assertThrows(NullPointerException.class, () -> checker.check(payload, null));
        }

        @Test
        @DisplayName("Returns unmodifiable empty list on empty tokens")
        void testEmptyTokens() {
            SourcePayload payload = SourcePayload.snippet("", Language.JAVA);
            List<Diagnostic> diagnostics = checker.check(payload, Collections.emptyList());
            assertTrue(diagnostics.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> diagnostics.add(null));
        }
    }

    // =========================================================================
    // 2. Valid Program Tests (Zero False Positives)
    // =========================================================================

    @Nested
    @DisplayName("Valid Program Tests (Zero False Positives)")
    class ValidProgramTests {

        @Test
        @DisplayName("Java: Comprehensive valid program produces zero diagnostics")
        void testValidJavaProgram() {
            String code = """
                package com.example.app;

                import java.util.List;
                import java.util.ArrayList;

                public class ValidApp {
                    private int count = 0;

                    public ValidApp(int initial) {
                        this.count = initial;
                    }

                    public void execute() {
                        int x = 10;
                        x += 5;
                        if (x > 10) {
                            System.out.println("greater");
                        } else {
                            System.out.println("lesser");
                        }

                        for (int i = 0; i < 10; i++) {
                            count += i;
                        }

                        while (count > 0) {
                            count--;
                        }

                        do {
                            count++;
                        } while (count < 5);

                        switch (count) {
                            case 1:
                                break;
                            default:
                                break;
                        }

                        try {
                            List<String> list = new ArrayList<>();
                            list.add("test");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                interface Service {
                    void serve();
                }

                record Point(int x, int y) {}

                enum Direction {
                    NORTH, SOUTH, EAST, WEST
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics, got: " + diagnostics);
        }

        @Test
        @DisplayName("C++: Comprehensive valid program produces zero diagnostics")
        void testValidCppProgram() {
            String code = """
                #include <iostream>
                #include <vector>

                namespace Geometry {
                    struct Point {
                        int x;
                        int y;
                    };

                    class Shape {
                    public:
                        virtual void draw() = 0;
                    };

                    enum Color {
                        RED, GREEN, BLUE
                    };
                }

                void printNumber(int n) {
                    std::cout << n << std::endl;
                }

                int main() {
                    int a = 10;
                    int b = 20;
                    int sum = a + b;

                    for (int i = 0; i < 10; i++) {
                        sum += i;
                    }

                    do {
                        sum--;
                    } while (sum > 20);

                    return 0;
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.CPP);
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics, got: " + diagnostics);
        }

        @Test
        @DisplayName("Python: Comprehensive valid script produces zero diagnostics")
        void testValidPythonProgram() {
            String code = """
                import math

                class Calculator:
                    def __init__(self, val):
                        self.val = val

                    def add(self, x):
                        return self.val + x

                def process(data):
                    if data > 0:
                        res = data * 2
                    elif data == 0:
                        res = 0
                    else:
                        res = -data

                    for i in range(5):
                        res += i

                    while res > 100:
                        res //= 2

                    try:
                        val = 10 / res
                    except ZeroDivisionError:
                        val = 0
                    finally:
                        pass

                    with open("sample.txt") as f:
                        content = f.read()

                    match res:
                        case 0:
                            print("zero")
                        case _:
                            print("other")

                    # Semicolons as optional statement separators
                    a = 1; b = 2; c = a + b

                    # Multi-line parenthesized expression
                    total = (
                        a +
                        b +
                        c
                    )
                    return total

                # Single-line suite
                if True: return 1
                """;
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics, got: " + diagnostics);
        }
    }

    // =========================================================================
    // 3. Java Terminator Tests
    // =========================================================================

    @Nested
    @DisplayName("Java Terminator Tests")
    class JavaTerminatorTests {

        @Test
        @DisplayName("Flags missing semicolon on package declaration")
        void testMissingSemicolonPackage() {
            String code = "package com.example.app\n public class App {}";
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals(CheckCategory.STATEMENT_TERMINATOR, d.category());
            assertEquals("Insert ';'", d.suggestedFix().orElseThrow());
        }

        @Test
        @DisplayName("Flags missing semicolon on import declaration")
        void testMissingSemicolonImport() {
            String code = "import java.util.List\n public class App {}";
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals("Insert ';'", d.suggestedFix().orElseThrow());
        }

        @Test
        @DisplayName("Flags missing semicolon on variable declaration")
        void testMissingSemicolonVarDecl() {
            String code = """
                public class App {
                    void run() {
                        int x = 42
                        int y = 10;
                    }
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals(3, d.location().line());
            assertEquals("Insert ';'", d.suggestedFix().orElseThrow());
        }

        @Test
        @DisplayName("Flags missing semicolon on field declaration")
        void testMissingSemicolonFieldDecl() {
            String code = """
                public class App {
                    private int count = 0
                    public void run() {}
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags missing semicolon on assignment expression")
        void testMissingSemicolonAssignment() {
            String code = """
                public class App {
                    void run() {
                        int x = 10;
                        x = 20
                        System.out.println(x);
                    }
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals(4, d.location().line());
        }

        @Test
        @DisplayName("Flags missing semicolon on return statement")
        void testMissingSemicolonReturn() {
            String code = """
                public class App {
                    int calc() {
                        return 42
                    }
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing semicolon on throw statement")
        void testMissingSemicolonThrow() {
            String code = """
                public class App {
                    void check() {
                        throw new IllegalArgumentException("invalid")
                    }
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing semicolon on break and continue statements")
        void testMissingSemicolonBreakContinue() {
            String code = """
                public class App {
                    void loop() {
                        while (true) {
                            if (false) continue
                            break
                        }
                    }
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertEquals(2, diagnostics.size());
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, diagnostics.get(0).code());
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, diagnostics.get(1).code());
        }

        @Test
        @DisplayName("Flags missing semicolon on do-while statement")
        void testMissingSemicolonDoWhile() {
            String code = """
                public class App {
                    void run() {
                        int x = 0;
                        do {
                            x++;
                        } while (x < 5)
                        int y = 10;
                    }
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals(6, d.location().line());
        }

        @Test
        @DisplayName("Never requires semicolon after compound {} blocks")
        void testNoFalsePositivesOnCompoundBlocks() {
            String code = """
                public class Foo {
                    public void method() {
                        if (true) {
                            int x = 1;
                        }
                        for (int i = 0; i < 10; i++) {
                            int y = 2;
                        }
                        while (false) {
                            int z = 3;
                        }
                        switch (1) {
                            case 1: break;
                        }
                    }
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics, got: " + diagnostics);
        }
    }

    // =========================================================================
    // 4. C++ Terminator Tests
    // =========================================================================

    @Nested
    @DisplayName("C++ Terminator Tests")
    class CppTerminatorTests {

        @Test
        @DisplayName("Flags missing semicolon on struct declaration")
        void testMissingSemicolonStruct() {
            String code = """
                struct Point {
                    int x;
                    int y;
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.CPP);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals(CheckCategory.STATEMENT_TERMINATOR, d.category());
            assertEquals("Insert ';'", d.suggestedFix().orElseThrow());
        }

        @Test
        @DisplayName("Flags missing semicolon on class declaration")
        void testMissingSemicolonClass() {
            String code = """
                class Widget {
                public:
                    void show();
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.CPP);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
        }

        @Test
        @DisplayName("Flags missing semicolon on enum declaration")
        void testMissingSemicolonEnum() {
            String code = """
                enum Status {
                    OK, ERROR
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.CPP);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
        }

        @Test
        @DisplayName("Flags missing semicolon on variable declaration")
        void testMissingSemicolonVarDecl() {
            String code = "int x = 10\nint y = 20;";
            List<Diagnostic> diagnostics = check(code, Language.CPP);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags missing semicolon on statements inside function")
        void testMissingSemicolonInFunction() {
            String code = """
                int main() {
                    int x = 10;
                    x = 20
                    return 0
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.CPP);
            assertEquals(2, diagnostics.size());
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, diagnostics.get(0).code());
            assertEquals(3, diagnostics.get(0).location().line());
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, diagnostics.get(1).code());
            assertEquals(4, diagnostics.get(1).location().line());
        }

        @Test
        @DisplayName("Flags missing semicolon on do-while statement")
        void testMissingSemicolonDoWhile() {
            String code = """
                int main() {
                    int x = 0;
                    do {
                        x++;
                    } while (x < 10)
                    return 0;
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.CPP);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            assertEquals(5, d.location().line());
        }

        @Test
        @DisplayName("Never requires semicolon after function definitions or namespace blocks")
        void testNoFalsePositivesFunctionAndNamespace() {
            String code = """
                namespace Core {
                    void helper() {
                        int x = 1;
                    }
                }

                void execute() {
                    int a = 10;
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.CPP);
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics, got: " + diagnostics);
        }
    }

    // =========================================================================
    // 5. Python Block Tests
    // =========================================================================

    @Nested
    @DisplayName("Python Block Tests")
    class PythonBlockTests {

        @Test
        @DisplayName("Flags missing colon on def header")
        void testMissingColonDef() {
            String code = """
                def foo(x, y)
                    return x + y
                """;
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_COLON, d.code());
            assertEquals(CheckCategory.STATEMENT_TERMINATOR, d.category());
            assertEquals(1, d.location().line());
            assertEquals("Insert ':'", d.suggestedFix().orElseThrow());
        }

        @Test
        @DisplayName("Flags missing colon across compound headers (class, if, for, while, try, with, match)")
        void testMissingColonAcrossHeaders() {
            String code = """
                class User
                    pass

                if True
                    pass

                for i in range(5)
                    pass

                while False
                    pass

                try
                    pass
                except
                    pass
                finally
                    pass

                with open("file") as f
                    pass

                match val
                    case 1
                        pass
                """;
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertEquals(10, diagnostics.size());
            for (Diagnostic d : diagnostics) {
                assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_COLON, d.code());
                assertEquals("Insert ':'", d.suggestedFix().orElseThrow());
            }
        }

        @Test
        @DisplayName("Flags missing indented block after compound header")
        void testExpectedIndentedBlock() {
            String code = """
                def foo():
                x = 1
                """;
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_EXPECTED_INDENTED_BLOCK, d.code());
            assertEquals(CheckCategory.STATEMENT_TERMINATOR, d.category());
            assertEquals(2, d.location().line());
            assertEquals(1, d.location().column());
            assertEquals("Indent statement block", d.suggestedFix().orElseThrow());
        }

        @Test
        @DisplayName("Flags missing indented block at end of file")
        void testExpectedIndentedBlockAtEof() {
            String code = "def foo():\n";
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_EXPECTED_INDENTED_BLOCK, d.code());
            assertEquals("Indent statement block", d.suggestedFix().orElseThrow());
        }

        @Test
        @DisplayName("Flags unexpected indentation at module top level")
        void testUnexpectedIndentTopLevel() {
            String code = "    x = 10\n";
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_UNEXPECTED_INDENT, d.code());
            assertEquals(1, d.location().line());
            assertEquals(1, d.location().column());
            assertEquals("Align indentation with enclosing block", d.suggestedFix().orElseThrow());
        }

        @Test
        @DisplayName("Flags unexpected indentation within statement sequence")
        void testUnexpectedIndentWithinBlock() {
            String code = """
                def foo():
                    x = 1
                        y = 2
                    return x + y
                """;
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_UNEXPECTED_INDENT, d.code());
            assertEquals(3, d.location().line());
            assertEquals("Align indentation with enclosing block", d.suggestedFix().orElseThrow());
        }

        @Test
        @DisplayName("Flags unmatched dedent that fails to align with any outer level")
        void testUnmatchedDedent() {
            String code = """
                def foo():
                    if True:
                        x = 1
                      y = 2
                    return x
                """;
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertEquals(1, diagnostics.size());
            Diagnostic d = diagnostics.get(0);
            assertEquals(StatementTerminatorAndBlockChecker.ERR_UNMATCHED_DEDENT, d.code());
            assertEquals(4, d.location().line());
            assertEquals(7, d.location().column());
            assertEquals("Realign dedent with prior block", d.suggestedFix().orElseThrow());
        }

        @Test
        @DisplayName("Does not mandate semicolons in Python")
        void testNoMandatorySemicolonInPython() {
            String code = """
                x = 10
                y = 20
                z = x + y
                """;
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertTrue(diagnostics.isEmpty());
        }
    }

    // =========================================================================
    // 6. Multiple Errors and Clean Recovery Tests
    // =========================================================================

    @Nested
    @DisplayName("Multiple Errors and Clean Recovery Tests")
    class MultipleErrorsAndCleanRecoveryTests {

        @Test
        @DisplayName("Java: Multiple sequential missing semicolons recover cleanly without cascade")
        void testJavaMultipleErrorsRecovery() {
            String code = """
                public class MultiErrors {
                    int a = 1
                    int b = 2
                    void test() {
                        int x = 10
                        int y = 20
                        return
                    }
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertEquals(5, diagnostics.size());
            for (Diagnostic d : diagnostics) {
                assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            }
            assertEquals(2, diagnostics.get(0).location().line());
            assertEquals(3, diagnostics.get(1).location().line());
            assertEquals(5, diagnostics.get(2).location().line());
            assertEquals(6, diagnostics.get(3).location().line());
            assertEquals(7, diagnostics.get(4).location().line());
        }

        @Test
        @DisplayName("C++: Multiple sequential missing semicolons on structs and statements recover cleanly")
        void testCppMultipleErrorsRecovery() {
            String code = """
                struct S {
                    int a;
                }

                class C {
                    int b;
                }

                int main() {
                    int x = 1
                    int y = 2
                    return 0
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.CPP);
            assertEquals(5, diagnostics.size());
            for (Diagnostic d : diagnostics) {
                assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_SEMICOLON, d.code());
            }
            assertEquals(3, diagnostics.get(0).location().line());
            assertEquals(7, diagnostics.get(1).location().line());
            assertEquals(10, diagnostics.get(2).location().line());
            assertEquals(11, diagnostics.get(3).location().line());
            assertEquals(12, diagnostics.get(4).location().line());
        }

        @Test
        @DisplayName("Python: Multiple defects across colons and indentation recover cleanly")
        void testPythonMultipleErrorsRecovery() {
            String code = """
                def foo()
                    if True
                        x = 1
                      y = 2
                    return y
                """;
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertEquals(3, diagnostics.size());
            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_COLON, diagnostics.get(0).code());
            assertEquals(1, diagnostics.get(0).location().line());

            assertEquals(StatementTerminatorAndBlockChecker.ERR_MISSING_COLON, diagnostics.get(1).code());
            assertEquals(2, diagnostics.get(1).location().line());

            assertEquals(StatementTerminatorAndBlockChecker.ERR_UNMATCHED_DEDENT, diagnostics.get(2).code());
            assertEquals(4, diagnostics.get(2).location().line());
        }

        @Test
        @DisplayName("Python: Multiple missing indented blocks across functions")
        void testPythonMultipleMissingIndentedBlocks() {
            String code = """
                def first():
                def second():
                x = 1
                """;
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertEquals(2, diagnostics.size());
            assertEquals(StatementTerminatorAndBlockChecker.ERR_EXPECTED_INDENTED_BLOCK, diagnostics.get(0).code());
            assertEquals(2, diagnostics.get(0).location().line());
            assertEquals(StatementTerminatorAndBlockChecker.ERR_EXPECTED_INDENTED_BLOCK, diagnostics.get(1).code());
            assertEquals(3, diagnostics.get(1).location().line());
        }
    }
}
