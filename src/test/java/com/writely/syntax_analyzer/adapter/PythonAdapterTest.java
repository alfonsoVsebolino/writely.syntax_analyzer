package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.adapter.python.PythonDiagnosticCodes;
import com.writely.syntax_analyzer.adapter.python.ast.PyModule;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SyntaxNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PythonAdapter and PythonParser Tests")
class PythonAdapterTest {

    private PythonAdapter adapter;
    private ParserAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        adapter = new PythonAdapter();
        registry = ParserAdapterRegistry.createDefault();
    }

    @Nested
    @DisplayName("Contract, Registry & Rule Capabilities")
    class ContractAndRegistryTests {

        @Test
        @DisplayName("Exposes correct Python language contract properties")
        void testContractProperties() {
            assertEquals(Language.PYTHON, adapter.language());
            assertEquals("Python", adapter.displayName());
            assertTrue(adapter.supportedExtensions().contains(".py"));
            assertTrue(adapter.supportsExtension(".py"));
            assertTrue(adapter.supportsExtension("py"));
            assertFalse(adapter.supportsExtension(".java"));
            assertFalse(adapter.supportsExtension(".cpp"));
        }

        @Test
        @DisplayName("Plugs cleanly into default ParserAdapterRegistry")
        void testRegistryIntegration() {
            assertTrue(registry.hasAdapter(Language.PYTHON));
            ParserAdapter resolved = registry.getRequiredAdapter(Language.PYTHON);
            assertInstanceOf(PythonAdapter.class, resolved);

            ParserAdapter byExt = registry.getAdapterForExtension(".py").orElseThrow();
            assertInstanceOf(PythonAdapter.class, byExt);

            ParserAdapter byFile = registry.getAdapterForFileName("script.py").orElseThrow();
            assertInstanceOf(PythonAdapter.class, byFile);
        }

        @Test
        @DisplayName("Exposes Python rule capabilities")
        void testRuleCapabilities() {
            RuleCapabilities caps = adapter.ruleCapabilities();
            assertFalse(caps.requiresStatementTerminators());
            assertTrue(caps.supportsIndentationBlocks());
            assertTrue(caps.supportsCategory(CheckCategory.CONTROL_HEADER));
            assertTrue(caps.supportsCategory(CheckCategory.STATEMENT_TERMINATOR));
            assertTrue(caps.isKeyword("def"));
            assertTrue(caps.isKeyword("class"));
            assertTrue(caps.isKeyword("elif"));
            assertTrue(caps.isOperator("**"));
            assertTrue(caps.isOperator(":="));
        }

        @Test
        @DisplayName("Rejects null payload or mismatched language")
        void testPreconditions() {
            assertThrows(NullPointerException.class, () -> adapter.parse(null));
            SourcePayload javaPayload = SourcePayload.snippet("int x = 10;", Language.JAVA);
            assertThrows(IllegalArgumentException.class, () -> adapter.parse(javaPayload));
        }

        @Test
        @DisplayName("Handles empty source text gracefully")
        void testEmptySource() {
            ParseResult result = adapter.parse(SourcePayload.snippet("", Language.PYTHON));
            assertTrue(result.isSuccessful());
            assertFalse(result.hasErrors());
            assertTrue(result.hasSyntaxTree());
            assertEquals("Module", result.syntaxTree().orElseThrow().kind());
            assertTrue(result.hasNativeSyntaxTree());
            assertInstanceOf(PyModule.class, result.nativeSyntaxTree().orElseThrow());
        }
    }

    @Nested
    @DisplayName("Valid Python Scripts")
    class ValidPythonScriptTests {

        @Test
        @DisplayName("Parses imports, aliases, and from-imports")
        void testImports() {
            String code = """
                import math
                import os, sys
                import numpy as np, pandas as pd
                from math import sqrt, pi as PI
                from os.path import join, exists
                from . import local_mod
                from ..utils import helper as h
                from collections import *
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals("Module", root.kind());
            assertEquals(8, root.children().size());
            assertEquals("ImportStatement", root.children().get(0).kind());
            assertEquals("FromImportStatement", root.children().get(3).kind());
        }

        @Test
        @DisplayName("Parses variable assignments: simple, tuple, annotated, augmented")
        void testAssignments() {
            String code = """
                x = 10
                a, b = 1, 2
                total: int = 100
                name: str
                count += 1
                scale *= 2.5
                bits >>= 2
                power **= 3
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(8, root.children().size());
            assertEquals("AssignStatement", root.children().get(0).kind());
            assertEquals("AssignStatement", root.children().get(1).kind());
            assertEquals("AnnAssignStatement", root.children().get(2).kind());
            assertEquals("AnnAssignStatement", root.children().get(3).kind());
            assertEquals("AugAssignStatement", root.children().get(4).kind());
        }

        @Test
        @DisplayName("Parses functions: parameters, defaults, type annotations, varargs, kwargs")
        void testFunctions() {
            String code = """
                def simple():
                    pass

                def add(a, b):
                    return a + b

                def calculate(x: int, y: float = 3.14) -> float:
                    return x * y

                def varargs(first, *args, kw_only: bool = True, **kwargs):
                    return args
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(4, root.children().size());

            SyntaxNode fnCalc = root.children().get(2);
            assertEquals("FunctionDef", fnCalc.kind());
            assertEquals("calculate", fnCalc.label());
            assertEquals("calculate", fnCalc.attribute("name").orElseThrow());
            assertTrue(fnCalc.children().stream().anyMatch(c -> c.kind().equals("ParameterList")));
            assertTrue(fnCalc.children().stream().anyMatch(c -> c.kind().equals("ReturnType")));
            assertTrue(fnCalc.children().stream().anyMatch(c -> c.kind().equals("Block")));
        }

        @Test
        @DisplayName("Parses classes: inheritance, methods, and constructors")
        void testClasses() {
            String code = """
                class Animal:
                    def speak(self):
                        pass

                class Dog(Animal):
                    def __init__(self, name: str):
                        self.name = name

                    def speak(self) -> str:
                        return f"{self.name} says woof!"
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(2, root.children().size());

            SyntaxNode clsDog = root.children().get(1);
            assertEquals("ClassDef", clsDog.kind());
            assertEquals("Dog", clsDog.label());
            assertTrue(clsDog.children().stream().anyMatch(c -> c.kind().equals("BaseList")));
            assertTrue(clsDog.children().stream().anyMatch(c -> c.kind().equals("Block")));
        }

        @Test
        @DisplayName("Parses decorators on functions and classes")
        void testDecorators() {
            String code = """
                @staticmethod
                def static_worker():
                    return 42

                @property
                def value(self):
                    return self._val

                @route("/api/users", methods=["GET"])
                def get_users():
                    return []

                @dataclass
                class Point:
                    x: int
                    y: int
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(4, root.children().size());

            SyntaxNode fnStatic = root.children().get(0);
            assertEquals("FunctionDef", fnStatic.kind());
            assertTrue(fnStatic.children().stream().anyMatch(c -> c.kind().equals("Decorator")));

            SyntaxNode clsPoint = root.children().get(3);
            assertEquals("ClassDef", clsPoint.kind());
            assertTrue(clsPoint.children().stream().anyMatch(c -> c.kind().equals("Decorator")));
        }

        @Test
        @DisplayName("Parses conditionals: if, elif, else")
        void testConditionals() {
            String code = """
                if x > 10:
                    status = "high"
                elif x > 5:
                    status = "medium"
                elif x > 0:
                    status = "low"
                else:
                    status = "zero or negative"
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(1, root.children().size());
            SyntaxNode ifNode = root.children().get(0);
            assertEquals("IfStatement", ifNode.kind());
            assertTrue(ifNode.children().stream().anyMatch(c -> c.kind().equals("ThenBlock")));
            assertEquals(2, ifNode.children().stream().filter(c -> c.kind().equals("ElifClause")).count());
            assertTrue(ifNode.children().stream().anyMatch(c -> c.kind().equals("ElseBlock")));
        }

        @Test
        @DisplayName("Parses loops: for-in, while, break, continue, else")
        void testLoops() {
            String code = """
                for i in range(10):
                    if i == 5:
                        break
                    elif i == 2:
                        continue
                    print(i)
                else:
                    print("finished loop")

                while count > 0:
                    count -= 1
                else:
                    print("done")
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(2, root.children().size());
            assertEquals("ForStatement", root.children().get(0).kind());
            assertEquals("WhileStatement", root.children().get(1).kind());
        }

        @Test
        @DisplayName("Parses exception handling and context managers: try, except, finally, with")
        void testExceptionHandlingAndWith() {
            String code = """
                try:
                    risky_op()
                except ValueError as err:
                    handle_val(err)
                except (TypeError, KeyError):
                    handle_other()
                else:
                    success_op()
                finally:
                    cleanup()

                with open("test.txt") as f:
                    data = f.read()
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(2, root.children().size());
            assertEquals("TryStatement", root.children().get(0).kind());
            assertEquals("WithStatement", root.children().get(1).kind());
        }

        @Test
        @DisplayName("Parses pattern matching match-case statements")
        void testMatchCase() {
            String code = """
                match status:
                    case 200:
                        handle_ok()
                    case 404 if not retry:
                        handle_not_found()
                    case _:
                        handle_default()
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(1, root.children().size());
            assertEquals("MatchStatement", root.children().get(0).kind());
        }

        @Test
        @DisplayName("Parses expressions: comprehensions, ternaries, walrus, lambdas, slicing")
        void testExpressions() {
            String code = """
                squares = [x * 2 for x in nums if x > 0]
                lookup = {k: v for k, v in pairs}
                unique = {x for x in items}
                gen = (x for x in seq)
                flag = 1 if active else 0
                func = lambda a, b: a + b
                sub = arr[1:5:2]
                rev = arr[::-1]
                val = (n := len(items))
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(9, root.children().size());
        }

        @Test
        @DisplayName("Parses diverse Python string forms without errors")
        void testStringForms() {
            String code = """
                s1 = 'single quoted'
                s2 = "double quoted"
                s3 = '''triple single quoted'''
                s4 = \"\"\"triple double quoted\"\"\"
                s5 = f"hello {name}"
                s6 = r"raw\\path\\to\\file"
                s7 = b"byte string"
                s8 = rf"raw formatted {value}"
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(8, root.children().size());
        }

        @Test
        @DisplayName("Parses semicolon-separated statements on single logical lines")
        void testSemicolonSeparators() {
            String code = "x = 1; y = 2; z = x + y\nprint(z)";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Expected 0 errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(4, root.children().size());
        }

        @Test
        @DisplayName("Preserves native AST PyModule tree in ParseResult")
        void testNativeSyntaxTreePreservation() {
            String code = "def greet():\n    return 'hello'";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasNativeSyntaxTree());
            PyModule pyMod = result.nativeSyntaxTree(PyModule.class).orElseThrow();
            assertEquals(1, pyMod.statements().size());
        }
    }

    @Nested
    @DisplayName("Invalid & Malformed Python Inputs")
    class InvalidPythonScriptTests {

        @Test
        @DisplayName("Flags missing colon on def header")
        void testMissingColonOnDef() {
            String code = "def compute(x, y)\n    return x + y";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            List<Diagnostic> diags = result.diagnostics();
            assertTrue(diags.stream().anyMatch(d ->
                d.category() == CheckCategory.CONTROL_HEADER
                && d.code().equals(PythonDiagnosticCodes.ERR_MISSING_COLON)
                && d.location().line() == 1
            ));
        }

        @Test
        @DisplayName("Flags missing colon on class header")
        void testMissingColonOnClass() {
            String code = "class Calculator\n    pass";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.CONTROL_HEADER
                && d.code().equals(PythonDiagnosticCodes.ERR_MISSING_COLON)
                && d.location().line() == 1
            ));
        }

        @Test
        @DisplayName("Flags missing colon on if header")
        void testMissingColonOnIf() {
            String code = "if x > 10\n    print(x)";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.CONTROL_HEADER
                && d.code().equals(PythonDiagnosticCodes.ERR_MISSING_COLON)
                && d.location().line() == 1
            ));
        }

        @Test
        @DisplayName("Flags missing colon on for and while headers")
        void testMissingColonOnLoops() {
            String code = "for i in range(5)\n    pass\nwhile count < 10\n    count += 1";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertEquals(2, result.diagnostics().stream().filter(d ->
                d.code().equals(PythonDiagnosticCodes.ERR_MISSING_COLON)
            ).count());
        }

        @Test
        @DisplayName("Flags missing colon on elif and else headers")
        void testMissingColonOnElifElse() {
            String code = "if x > 0:\n    pass\nelif x < 0\n    pass\nelse\n    pass";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertEquals(2, result.diagnostics().stream().filter(d ->
                d.code().equals(PythonDiagnosticCodes.ERR_MISSING_COLON)
            ).count());
        }

        @Test
        @DisplayName("Flags expected indented block after colon header")
        void testExpectedIndentedBlock() {
            String code = "if True:\nprint('not indented')";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.STATEMENT_TERMINATOR
                && d.code().equals(PythonDiagnosticCodes.ERR_EXPECTED_INDENTED_BLOCK)
            ));
        }

        @Test
        @DisplayName("Flags unexpected indentation at module level")
        void testUnexpectedIndentation() {
            String code = "    x = 10\nprint(x)";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.STATEMENT_TERMINATOR
                && d.code().equals(PythonDiagnosticCodes.ERR_UNEXPECTED_INDENT)
                && d.location().line() == 1
            ));
        }

        @Test
        @DisplayName("Flags malformed if header missing condition expression")
        void testMalformedIfHeader() {
            String code = "if :\n    pass";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.CONTROL_HEADER
                && d.code().equals(PythonDiagnosticCodes.ERR_MALFORMED_HEADER)
            ));
        }

        @Test
        @DisplayName("Flags malformed for loop header missing target or in")
        void testMalformedForHeader() {
            String code = "for in items:\n    pass";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.CONTROL_HEADER
                && d.code().equals(PythonDiagnosticCodes.ERR_MALFORMED_HEADER)
            ));
        }

        @Test
        @DisplayName("Flags malformed def header missing function identifier")
        void testMalformedDefHeader() {
            String code = "def ():\n    pass";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.CONTROL_HEADER
                && d.code().equals(PythonDiagnosticCodes.ERR_MALFORMED_HEADER)
            ));
        }

        @Test
        @DisplayName("Flags unclosed delimiter in expression or header")
        void testUnclosedDelimiter() {
            String code = "if (x > 5:\n    print(1)";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.DELIMITER_MATCH
                && d.code().equals(PythonDiagnosticCodes.ERR_UNCLOSED_DELIMITER)
            ));
        }

        @Test
        @DisplayName("Flags invalid consecutive operators")
        void testInvalidOperatorSequence() {
            String code = "y = 20 + * 5";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.OPERATOR_SYNTAX
                && d.code().equals(PythonDiagnosticCodes.ERR_INVALID_SYNTAX)
            ));
        }

        @Test
        @DisplayName("Does not classify semantic name or type errors as syntax errors")
        void testSemanticErrorsNotClassifiedAsSyntaxErrors() {
            String code = """
                # Undeclared variable reference is a runtime/name error, not a syntax error
                print(undefined_variable_name)

                # Type mismatch annotation is a type error, not a syntax error
                count: int = "string_value"

                # Shadowing built-in names is valid syntax
                def len():
                    return 0

                # Calling non-callable literal is a runtime type error, not a syntax error
                res = "abc"()
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.PYTHON));
            assertFalse(result.hasErrors(), "Semantic errors should not be flagged as syntax errors: " + result.diagnostics());
            assertEquals(0, result.errorCount());
        }
    }
}
