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

@DisplayName("ControlStructureHeaderChecker Tests (Issue #23 / ADR 0014)")
class ControlStructureHeaderCheckerTest {

    private final ControlStructureHeaderChecker checker = new ControlStructureHeaderChecker();

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
        @DisplayName("Returns CONTROL_HEADER check category")
        void testCategory() {
            assertEquals(CheckCategory.CONTROL_HEADER, checker.category());
        }

        @Test
        @DisplayName("Rejects null payload or null tokens with NullPointerException")
        void testNullValidations() {
            SourcePayload payload = SourcePayload.snippet("if (true) {}", Language.JAVA);
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

        @Test
        @DisplayName("Whitespace or blank snippet yields zero diagnostics")
        void testBlankSnippet() {
            List<Diagnostic> javaDiags = check("   \n\t  \n  ", Language.JAVA);
            assertTrue(javaDiags.isEmpty());

            List<Diagnostic> pyDiags = check("   \n\t  \n  ", Language.PYTHON);
            assertTrue(pyDiags.isEmpty());

            List<Diagnostic> cppDiags = check("   \n\t  \n  ", Language.CPP);
            assertTrue(cppDiags.isEmpty());
        }
    }

    // =========================================================================
    // 2. Valid Header Tests (Zero False Positives)
    // =========================================================================

    @Nested
    @DisplayName("Valid Header Tests (Zero False Positives)")
    class ValidHeaderTests {

        @Test
        @DisplayName("Java: Valid control structures yield zero diagnostics")
        void testValidJavaHeaders() {
            String code = """
                package com.example;

                public class ValidJava {
                    public void execute(int x, java.util.List<String> list) {
                        if (x > 0) {
                            System.out.println("positive");
                        } else if (x == 0) {
                            System.out.println("zero");
                        } else {
                            System.out.println("negative");
                        }

                        while (x > 0) {
                            x--;
                        }

                        do {
                            x++;
                        } while (x < 10);

                        for (int i = 0; i < 10; i++) {
                            System.out.println(i);
                        }

                        for (;;) {
                            break;
                        }

                        for (; x < 20;) {
                            x++;
                        }

                        for (int i = 0, j = 10; i < j; i++, j--) {
                            x += i + j;
                        }

                        for (String item : list) {
                            System.out.println(item);
                        }

                        for (final var item : list) {
                            System.out.println(item);
                        }

                        switch (x) {
                            case 1 -> System.out.println("one");
                            default -> {}
                        }
                    }
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.JAVA);
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics but got: " + diagnostics);
        }

        @Test
        @DisplayName("C++: Valid control structures yield zero diagnostics")
        void testValidCppHeaders() {
            String code = """
                #include <iostream>
                #include <vector>

                void run(int x, const std::vector<int>& items) {
                    if (x > 0) {
                        std::cout << "positive";
                    } else if (x == 0) {
                        std::cout << "zero";
                    }

                    if constexpr (sizeof(int) == 4) {
                        std::cout << "32-bit int";
                    }

                    if (int val = x; val > 10) {
                        std::cout << val;
                    }

                    while (x > 0) {
                        x--;
                    }

                    do {
                        x++;
                    } while (x < 5);

                    for (int i = 0; i < 10; ++i) {
                        x += i;
                    }

                    for (;;) {
                        break;
                    }

                    for (const auto& item : items) {
                        std::cout << item;
                    }

                    switch (x) {
                        case 1: break;
                        default: break;
                    }
                }
                """;
            List<Diagnostic> diagnostics = check(code, Language.CPP);
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics but got: " + diagnostics);
        }

        @Test
        @DisplayName("Python: Valid control structures yield zero diagnostics")
        void testValidPythonHeaders() {
            String code = """
                def process(data):
                    if data > 0:
                        print("positive")
                    elif data == 0:
                        print("zero")
                    else:
                        print("negative")

                    while data > 0:
                        data -= 1

                    for item in [1, 2, 3]:
                        print(item)

                    for k, v in {"a": 1}.items():
                        print(k, v)

                    try:
                        risky()
                    except ValueError as e:
                        print(e)
                    except:
                        pass
                    finally:
                        cleanup()

                    with open("file.txt") as f:
                        content = f.read()

                    match data:
                        case 0:
                            print("zero")
                        case 1 if True:
                            print("one")
                        case _:
                            print("other")

                    # Comprehensions and inline expressions should not trigger header checks
                    squares = [x * x for x in range(10) if x % 2 == 0]
                    lookup = {k: v for k, v in [(1, 2)]}
                    msg = "yes" if data > 0 else "no"
                """;
            List<Diagnostic> diagnostics = check(code, Language.PYTHON);
            assertTrue(diagnostics.isEmpty(), () -> "Expected 0 diagnostics but got: " + diagnostics);
        }
    }

    // =========================================================================
    // 3. Java Header Tests
    // =========================================================================

    @Nested
    @DisplayName("Java Header Tests")
    class JavaHeaderTests {

        @Test
        @DisplayName("Flags missing opening parenthesis on 'if'")
        void testMissingOpenParenOnIf() {
            String code = """
                public class Test {
                    void test(int x) {
                        if x > 0 {
                            x++;
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(CheckCategory.CONTROL_HEADER, d.category());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing closing parenthesis on 'if'")
        void testMissingCloseParenOnIf() {
            String code = """
                public class Test {
                    void test(int x) {
                        if (x > 0 {
                            x++;
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing opening parenthesis on 'while'")
        void testMissingOpenParenOnWhile() {
            String code = """
                public class Test {
                    void test(int x) {
                        while x > 0) {
                            x--;
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing closing parenthesis on 'while'")
        void testMissingCloseParenOnWhile() {
            String code = """
                public class Test {
                    void test(int x) {
                        while (x > 0 {
                            x--;
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing opening parenthesis on 'switch'")
        void testMissingOpenParenOnSwitch() {
            String code = """
                public class Test {
                    void test(int x) {
                        switch x {
                            case 1 -> {}
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing closing parenthesis on 'switch'")
        void testMissingCloseParenOnSwitch() {
            String code = """
                public class Test {
                    void test(int x) {
                        switch (x {
                            case 1 -> {}
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing opening parenthesis on 'for'")
        void testMissingOpenParenOnFor() {
            String code = """
                public class Test {
                    void test() {
                        for int i = 0; i < 10; i++ {
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing closing parenthesis on 'for'")
        void testMissingCloseParenOnFor() {
            String code = """
                public class Test {
                    void test() {
                        for (int i = 0; i < 10; i++ {
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags empty condition on 'if'")
        void testEmptyConditionOnIf() {
            String code = """
                public class Test {
                    void test() {
                        if () {
                            System.out.println("empty");
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags empty condition on 'while'")
        void testEmptyConditionOnWhile() {
            String code = """
                public class Test {
                    void test() {
                        while () {
                            System.out.println("empty");
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags empty selector on 'switch'")
        void testEmptySelectorOnSwitch() {
            String code = """
                public class Test {
                    void test() {
                        switch () {
                            case 1 -> {}
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags malformed 'for' loop missing first semicolon")
        void testMalformedForMissingFirstSemicolon() {
            String code = """
                public class Test {
                    void test() {
                        for (int i = 0 i < 10; i++) {
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags malformed 'for' loop missing second semicolon")
        void testMalformedForMissingSecondSemicolon() {
            String code = """
                public class Test {
                    void test() {
                        for (int i = 0; i < 10) {
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags malformed 'for' loop missing all semicolons")
        void testMalformedForMissingAllSemicolons() {
            String code = """
                public class Test {
                    void test() {
                        for (int i = 0) {
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags malformed 'for' loop with too many semicolons")
        void testMalformedForTooManySemicolons() {
            String code = """
                public class Test {
                    void test() {
                        for (;;;) {
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags enhanced 'for' loop missing variable declaration")
        void testEnhancedForMissingVariable() {
            String code = """
                public class Test {
                    void test(java.util.List<String> list) {
                        for (: list) {
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags enhanced 'for' loop missing iterable expression")
        void testEnhancedForMissingIterable() {
            String code = """
                public class Test {
                    void test() {
                        for (String s :) {
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, d.code());
            assertEquals(3, d.location().line());
        }
    }

    // =========================================================================
    // 4. C++ Header Tests
    // =========================================================================

    @Nested
    @DisplayName("Cpp Header Tests")
    class CppHeaderTests {

        @Test
        @DisplayName("Flags missing opening parenthesis on C++ 'if'")
        void testMissingOpenParenOnCppIf() {
            String code = "void run(int x) {\n    if x > 0 {\n        return;\n    }\n}";
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags missing closing parenthesis on C++ 'if'")
        void testMissingCloseParenOnCppIf() {
            String code = "void run(int x) {\n    if (x > 0 {\n        return;\n    }\n}";
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags missing opening parenthesis on C++ 'while'")
        void testMissingOpenParenOnCppWhile() {
            String code = "void run(int x) {\n    while count > 0) {\n        count--;\n    }\n}";
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags missing closing parenthesis on C++ 'while'")
        void testMissingCloseParenOnCppWhile() {
            String code = "void run(int x) {\n    while (count > 0 {\n        count--;\n    }\n}";
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags missing opening parenthesis on C++ 'switch'")
        void testMissingOpenParenOnCppSwitch() {
            String code = "void run(int val) {\n    switch val {\n        case 1: break;\n    }\n}";
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags missing closing parenthesis on C++ 'switch'")
        void testMissingCloseParenOnCppSwitch() {
            String code = "void run(int val) {\n    switch (val {\n        case 1: break;\n    }\n}";
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags missing opening parenthesis on C++ 'for'")
        void testMissingOpenParenOnCppFor() {
            String code = "void run() {\n    for int i = 0; i < 10; ++i {\n    }\n}";
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags missing closing parenthesis on C++ 'for'")
        void testMissingCloseParenOnCppFor() {
            String code = "void run() {\n    for (int i = 0; i < 10; ++i {\n    }\n}";
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags empty condition on C++ 'while'")
        void testEmptyConditionOnCppWhile() {
            String code = "void run() {\n    while () {\n    }\n}";
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags malformed C++ 'for' loop missing semicolon")
        void testMalformedCppFor() {
            String code = "void run() {\n    for (int i = 0; i < 10) {\n    }\n}";
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, d.code());
            assertEquals(2, d.location().line());
        }
    }

    // =========================================================================
    // 5. Python Header Tests
    // =========================================================================

    @Nested
    @DisplayName("Python Header Tests")
    class PythonHeaderTests {

        @Test
        @DisplayName("Flags missing colon on 'if'")
        void testMissingColonOnIf() {
            String code = "if x > 0\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags missing colon on 'elif'")
        void testMissingColonOnElif() {
            String code = "if x > 0:\n    pass\nelif x < 0\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing colon on 'else'")
        void testMissingColonOnElse() {
            String code = "if x > 0:\n    pass\nelse\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing colon on 'while'")
        void testMissingColonOnWhile() {
            String code = "while count > 0\n    count -= 1";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags missing colon on 'for'")
        void testMissingColonOnFor() {
            String code = "for item in items\n    print(item)";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags missing colon on 'try'")
        void testMissingColonOnTry() {
            String code = "try\n    risky()\nexcept:\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags missing colon on 'except'")
        void testMissingColonOnExcept() {
            String code = "try:\n    risky()\nexcept ValueError\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing colon on 'finally'")
        void testMissingColonOnFinally() {
            String code = "try:\n    risky()\nfinally\n    cleanup()";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags missing colon on 'with'")
        void testMissingColonOnWith() {
            String code = "with open('f.txt') as f\n    f.read()";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags missing colon on 'match'")
        void testMissingColonOnMatch() {
            String code = "match val\n    case 1:\n        pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags missing colon on 'case'")
        void testMissingColonOnCase() {
            String code = "match val:\n    case 1\n        pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, d.code());
            assertEquals(2, d.location().line());
        }

        @Test
        @DisplayName("Flags missing 'in' keyword in 'for' loop header")
        void testMissingInInFor() {
            String code = "for item items:\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags missing target variable in Python 'for'")
        void testMissingTargetInPythonFor() {
            String code = "for in items:\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags missing iterable in Python 'for'")
        void testMissingIterableInPythonFor() {
            String code = "for x in:\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags empty condition on Python 'if'")
        void testEmptyConditionOnPythonIf() {
            String code = "if :\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags empty condition on Python 'elif'")
        void testEmptyConditionOnPythonElif() {
            String code = "if x > 0:\n    pass\nelif :\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags empty condition on Python 'while'")
        void testEmptyConditionOnPythonWhile() {
            String code = "while :\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags expression present on Python 'else'")
        void testForbiddenConditionOnPythonElse() {
            String code = "if x > 0:\n    pass\nelse x < 0:\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, d.code());
            assertEquals(3, d.location().line());
        }

        @Test
        @DisplayName("Flags expression present on Python 'try'")
        void testForbiddenConditionOnPythonTry() {
            String code = "try x:\n    pass\nexcept:\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, d.code());
            assertEquals(1, d.location().line());
        }

        @Test
        @DisplayName("Flags expression present on Python 'finally'")
        void testForbiddenConditionOnPythonFinally() {
            String code = "try:\n    pass\nfinally x:\n    pass";
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(1, diags.size());
            Diagnostic d = diags.get(0);
            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, d.code());
            assertEquals(3, d.location().line());
        }
    }

    // =========================================================================
    // 6. Multiple Errors and Clean Recovery Tests
    // =========================================================================

    @Nested
    @DisplayName("Multiple Errors and Clean Recovery Tests")
    class MultipleErrorsAndCleanRecoveryTests {

        @Test
        @DisplayName("Java: Multiple sequential header defects report all errors with clean recovery")
        void testMultipleSequentialJavaErrors() {
            String code = """
                public class MultiErrors {
                    void test(int x) {
                        if x > 0 {
                            x++;
                        }
                        while (x > 0 {
                            x--;
                        }
                        switch x {
                            case 1 -> {}
                        }
                        for int i = 0; i < 10; i++ {
                            x++;
                        }
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.JAVA);
            assertEquals(4, diags.size(), () -> "Expected 4 diagnostics but got: " + diags);

            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, diags.get(0).code());
            assertEquals(3, diags.get(0).location().line());

            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, diags.get(1).code());
            assertEquals(6, diags.get(1).location().line());

            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, diags.get(2).code());
            assertEquals(9, diags.get(2).location().line());

            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, diags.get(3).code());
            assertEquals(12, diags.get(3).location().line());
        }

        @Test
        @DisplayName("Python: Multiple sequential header defects report all errors with clean recovery")
        void testMultipleSequentialPythonErrors() {
            String code = """
                if x > 0
                    pass
                for y in items
                    pass
                while z > 0
                    pass
                """;
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(3, diags.size(), () -> "Expected 3 diagnostics but got: " + diags);

            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, diags.get(0).code());
            assertEquals(1, diags.get(0).location().line());

            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, diags.get(1).code());
            assertEquals(3, diags.get(1).location().line());

            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, diags.get(2).code());
            assertEquals(5, diags.get(2).location().line());
        }

        @Test
        @DisplayName("Python: Heterogeneous header defects detected cleanly in sequence")
        void testHeterogeneousPythonErrors() {
            String code = """
                if :
                    pass
                for y seq:
                    pass
                try
                    pass
                except:
                    pass
                """;
            List<Diagnostic> diags = check(code, Language.PYTHON);
            assertEquals(3, diags.size(), () -> "Expected 3 diagnostics but got: " + diags);

            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_CONTROL_HEADER, diags.get(0).code());
            assertEquals(1, diags.get(0).location().line());

            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, diags.get(1).code());
            assertEquals(3, diags.get(1).location().line());

            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_COLON, diags.get(2).code());
            assertEquals(5, diags.get(2).location().line());
        }

        @Test
        @DisplayName("C++: Multiple sequential header defects report all errors with clean recovery")
        void testMultipleSequentialCppErrors() {
            String code = """
                void run(int x, int count) {
                    if x > 0 {
                        return;
                    }
                    while (count > 0 {
                        count--;
                    }
                    for (int i = 0 i < 10; ++i) {
                        x++;
                    }
                }
                """;
            List<Diagnostic> diags = check(code, Language.CPP);
            assertEquals(3, diags.size(), () -> "Expected 3 diagnostics but got: " + diags);

            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, diags.get(0).code());
            assertEquals(2, diags.get(0).location().line());

            assertEquals(ControlStructureHeaderChecker.ERR_MISSING_HEADER_DELIMITER, diags.get(1).code());
            assertEquals(5, diags.get(1).location().line());

            assertEquals(ControlStructureHeaderChecker.ERR_MALFORMED_FOR_HEADER, diags.get(2).code());
            assertEquals(8, diags.get(2).location().line());
        }
    }
}
