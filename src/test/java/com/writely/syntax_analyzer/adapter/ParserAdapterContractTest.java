package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Parser Adapter Contract Tests")
class ParserAdapterContractTest {

    @Nested
    @DisplayName("ParseResult Tests")
    class ParseResultTests {

        @Test
        @DisplayName("Constructing ParseResult preserves syntax tree, diagnostics, tokens, and native tree")
        void testParseResultPreservesInformation() {
            SourceSpan span = SourceSpan.of(SourceLocation.of(1, 1, 0), SourceLocation.of(1, 10, 9));
            SyntaxNode tree = SyntaxNode.of("CompilationUnit", "Test.java", span);
            Diagnostic diag = Diagnostic.error(CheckCategory.STATEMENT_TERMINATOR, SourceLocation.of(1, 10, 9), "Missing semicolon", "ERR_SEMI");
            Token token = Token.of(TokenType.IDENTIFIER, "foo", span);
            Object nativeTree = new Object();

            ParseResult result = ParseResult.of(tree, List.of(diag), nativeTree, List.of(token));

            assertTrue(result.hasSyntaxTree());
            assertEquals(tree, result.syntaxTree().orElseThrow());
            assertTrue(result.hasNativeSyntaxTree());
            assertSame(nativeTree, result.nativeSyntaxTree().orElseThrow());
            assertEquals(Optional.of(nativeTree), result.nativeSyntaxTree(Object.class));
            assertEquals(Optional.empty(), result.nativeSyntaxTree(String.class));
            assertEquals(1, result.diagnostics().size());
            assertEquals(diag, result.diagnostics().get(0));
            assertEquals(1, result.tokens().size());
            assertEquals(token, result.tokens().get(0));
            assertTrue(result.hasErrors());
            assertFalse(result.isSuccessful());
            assertEquals(1, result.errorCount());
            assertEquals(0, result.warningCount());
        }

        @Test
        @DisplayName("ParseResult empty and factory methods")
        void testParseResultFactoryMethods() {
            ParseResult empty = ParseResult.empty();
            assertFalse(empty.hasSyntaxTree());
            assertFalse(empty.hasNativeSyntaxTree());
            assertTrue(empty.diagnostics().isEmpty());
            assertTrue(empty.tokens().isEmpty());
            assertFalse(empty.hasErrors());
            assertTrue(empty.isSuccessful());

            SyntaxNode node = SyntaxNode.leaf("Identifier", "x", SourceSpan.point(SourceLocation.start()));
            ParseResult singleNode = ParseResult.of(node);
            assertTrue(singleNode.hasSyntaxTree());
            assertTrue(singleNode.isSuccessful());

            Diagnostic warn = Diagnostic.warning(CheckCategory.IDENTIFIER_NAMING, SourceLocation.start(), "Naming warning", "WARN_NAME");
            ParseResult diagOnly = ParseResult.ofDiagnostics(List.of(warn));
            assertFalse(diagOnly.hasErrors());
            assertEquals(1, diagOnly.warningCount());

            Token tok = Token.of(TokenType.KEYWORD, "class", SourceSpan.point(SourceLocation.start()));
            ParseResult tokOnly = ParseResult.ofTokens(List.of(tok));
            assertEquals(1, tokOnly.tokens().size());
        }

        @Test
        @DisplayName("ParseResult enforces non-null invariants")
        void testParseResultInvariants() {
            assertThrows(NullPointerException.class, () -> new ParseResult(null, List.of(), Optional.empty(), List.of()));
            assertThrows(NullPointerException.class, () -> new ParseResult(Optional.empty(), List.of(), null, List.of()));
        }
    }

    @Nested
    @DisplayName("RuleCapabilities Tests")
    class RuleCapabilitiesTests {

        @Test
        @DisplayName("Java capabilities expose correct language characteristics")
        void testJavaCapabilities() {
            RuleCapabilities caps = RuleCapabilities.forJava();
            assertTrue(caps.requiresStatementTerminators());
            assertFalse(caps.supportsIndentationBlocks());
            assertTrue(caps.supportsCategory(CheckCategory.STATEMENT_TERMINATOR));
            assertTrue(caps.supportsCategory(CheckCategory.DELIMITER_MATCH));
            assertTrue(caps.supportsCategory(CheckCategory.IDENTIFIER_NAMING));

            assertTrue(caps.isKeyword("class"));
            assertTrue(caps.isKeyword("public"));
            assertTrue(caps.isKeyword("void"));
            assertFalse(caps.isKeyword("def"));

            assertTrue(caps.isOperator("+"));
            assertTrue(caps.isOperator(">>>="));
            assertTrue(caps.isOperator("->"));
            assertFalse(caps.isOperator(":="));

            assertTrue(caps.isControlKeyword("if"));
            assertTrue(caps.isControlKeyword("switch"));
            assertFalse(caps.isControlKeyword("def"));
        }

        @Test
        @DisplayName("Python capabilities expose correct language characteristics")
        void testPythonCapabilities() {
            RuleCapabilities caps = RuleCapabilities.forPython();
            assertFalse(caps.requiresStatementTerminators());
            assertTrue(caps.supportsIndentationBlocks());
            assertTrue(caps.supportsCategory(CheckCategory.CONTROL_HEADER));

            assertTrue(caps.isKeyword("def"));
            assertTrue(caps.isKeyword("lambda"));
            assertFalse(caps.isKeyword("public"));

            assertTrue(caps.isOperator("**"));
            assertTrue(caps.isOperator("//"));
            assertTrue(caps.isOperator(":="));

            assertTrue(caps.isControlKeyword("elif"));
            assertTrue(caps.isControlKeyword("with"));
        }

        @Test
        @DisplayName("C++ capabilities expose correct language characteristics")
        void testCppCapabilities() {
            RuleCapabilities caps = RuleCapabilities.forCpp();
            assertTrue(caps.requiresStatementTerminators());
            assertFalse(caps.supportsIndentationBlocks());

            assertTrue(caps.isKeyword("namespace"));
            assertTrue(caps.isKeyword("template"));
            assertTrue(caps.isKeyword("constexpr"));

            assertTrue(caps.isOperator("::"));
            assertTrue(caps.isOperator("->*"));
            assertTrue(caps.isOperator("<=>"));

            assertTrue(caps.isControlKeyword("while"));
            assertTrue(caps.isControlKeyword("catch"));
        }

        @Test
        @DisplayName("Custom RuleCapabilities via Builder")
        void testCustomCapabilitiesBuilder() {
            RuleCapabilities caps = RuleCapabilities.builder()
                .supportedCategories(Set.of(CheckCategory.OPERATOR_SYNTAX))
                .requiresStatementTerminators(true)
                .supportsIndentationBlocks(false)
                .keywords(Set.of("customKey"))
                .operators(Set.of("<>"))
                .controlKeywords(Set.of("customCtrl"))
                .build();

            assertTrue(caps.supportsCategory(CheckCategory.OPERATOR_SYNTAX));
            assertFalse(caps.supportsCategory(CheckCategory.DELIMITER_MATCH));
            assertTrue(caps.isKeyword("customKey"));
            assertTrue(caps.isOperator("<>"));
            assertTrue(caps.isControlKeyword("customCtrl"));
        }
    }

    @Nested
    @DisplayName("Language Adapter Implementation Tests")
    class AdapterImplementationTests {

        @Test
        @DisplayName("JavaAdapter exposes correct contract properties and parses stub")
        void testJavaAdapter() {
            JavaAdapter adapter = new JavaAdapter();
            assertEquals(Language.JAVA, adapter.language());
            assertEquals("Java", adapter.displayName());
            assertTrue(adapter.supportedExtensions().contains(".java"));
            assertTrue(adapter.supportsExtension(".java"));
            assertTrue(adapter.supportsExtension("java"));
            assertFalse(adapter.supportsExtension(".py"));

            SourcePayload payload = SourcePayload.singleLine("int x = 42;", Language.JAVA);
            List<Token> tokens = adapter.tokenize(payload);
            assertFalse(tokens.isEmpty());

            ParseResult result = adapter.parse(payload);
            assertTrue(result.hasSyntaxTree());
            assertEquals("CompilationUnit", result.syntaxTree().get().kind());
            assertEquals(payload.sourceName(), result.syntaxTree().get().label());
            assertFalse(result.hasErrors());
            assertEquals(tokens.size(), result.tokens().size());

            SourcePayload wrongLang = SourcePayload.singleLine("x = 10", Language.PYTHON);
            assertThrows(IllegalArgumentException.class, () -> adapter.parse(wrongLang));
        }

        @Test
        @DisplayName("PythonAdapter exposes correct contract properties and parses stub")
        void testPythonAdapter() {
            PythonAdapter adapter = new PythonAdapter();
            assertEquals(Language.PYTHON, adapter.language());
            assertEquals("Python", adapter.displayName());
            assertTrue(adapter.supportedExtensions().contains(".py"));
            assertTrue(adapter.supportsExtension(".py"));
            assertTrue(adapter.supportsExtension("py"));
            assertFalse(adapter.supportsExtension(".cpp"));

            SourcePayload payload = SourcePayload.snippet("x = 10\nprint(x)", Language.PYTHON);
            List<Token> tokens = adapter.tokenize(payload);
            assertFalse(tokens.isEmpty());

            ParseResult result = adapter.parse(payload);
            assertTrue(result.hasSyntaxTree());
            assertEquals("Module", result.syntaxTree().get().kind());
            assertFalse(result.hasErrors());

            SourcePayload wrongLang = SourcePayload.singleLine("int x;", Language.JAVA);
            assertThrows(IllegalArgumentException.class, () -> adapter.parse(wrongLang));
        }

        @Test
        @DisplayName("CppAdapter exposes correct contract properties and parses stub")
        void testCppAdapter() {
            CppAdapter adapter = new CppAdapter();
            assertEquals(Language.CPP, adapter.language());
            assertEquals("C++", adapter.displayName());
            assertTrue(adapter.supportedExtensions().containsAll(List.of(".cpp", ".cxx", ".cc", ".h", ".hpp")));
            assertTrue(adapter.supportsExtension(".cpp"));
            assertTrue(adapter.supportsExtension(".hpp"));
            assertTrue(adapter.supportsExtension("cxx"));
            assertFalse(adapter.supportsExtension(".java"));

            SourcePayload payload = SourcePayload.singleLine("int x = 10;", Language.CPP);
            List<Token> tokens = adapter.tokenize(payload);
            assertFalse(tokens.isEmpty());

            ParseResult result = adapter.parse(payload);
            assertTrue(result.hasSyntaxTree());
            assertEquals("TranslationUnit", result.syntaxTree().get().kind());
            assertFalse(result.hasErrors());

            SourcePayload wrongLang = SourcePayload.singleLine("x = 10", Language.PYTHON);
            assertThrows(IllegalArgumentException.class, () -> adapter.parse(wrongLang));
        }

        @Test
        @DisplayName("Custom parser delegate can be plugged into adapters")
        void testCustomParserDelegate() {
            ParseResult customResult = ParseResult.of(
                SyntaxNode.leaf("CustomNode", "CustomLabel", SourceSpan.point(SourceLocation.start())),
                List.of(Diagnostic.error(CheckCategory.OPERATOR_SYNTAX, SourceLocation.start(), "Bad operator", "ERR_OP"))
            );

            JavaAdapter customAdapter = new JavaAdapter((p, t) -> customResult);
            SourcePayload payload = SourcePayload.singleLine("int x = 1;", Language.JAVA);
            ParseResult result = customAdapter.parse(payload);

            assertSame(customResult, result);
            assertTrue(result.hasErrors());
        }
    }
}
