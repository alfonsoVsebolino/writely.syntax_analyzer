package com.writely.syntax_analyzer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Shared Analysis Domain Model Tests")
class DomainModelTest {

    @Nested
    @DisplayName("Language Enum Tests")
    class LanguageTests {

        @Test
        @DisplayName("All required languages are present with canonical extensions")
        void testRequiredLanguagesPresent() {
            assertEquals(3, Language.values().length);
            assertEquals("Java", Language.JAVA.displayName());
            assertEquals("Python", Language.PYTHON.displayName());
            assertEquals("C++", Language.CPP.displayName());

            assertEquals(".java", Language.JAVA.primaryExtension());
            assertTrue(Language.JAVA.extensions().contains(".java"));

            assertEquals(".py", Language.PYTHON.primaryExtension());
            assertTrue(Language.PYTHON.extensions().contains(".py"));

            assertEquals(".cpp", Language.CPP.primaryExtension());
            assertTrue(Language.CPP.extensions().containsAll(List.of(".cpp", ".cxx", ".cc", ".h", ".hpp")));
        }

        @Test
        @DisplayName("Resolution from file extension with and without leading dot")
        void testFromExtension() {
            assertEquals(Optional.of(Language.JAVA), Language.fromExtension(".java"));
            assertEquals(Optional.of(Language.JAVA), Language.fromExtension("java"));
            assertEquals(Optional.of(Language.JAVA), Language.fromExtension("JAVA"));

            assertEquals(Optional.of(Language.PYTHON), Language.fromExtension(".py"));
            assertEquals(Optional.of(Language.PYTHON), Language.fromExtension("py"));

            assertEquals(Optional.of(Language.CPP), Language.fromExtension(".cpp"));
            assertEquals(Optional.of(Language.CPP), Language.fromExtension("cpp"));
            assertEquals(Optional.of(Language.CPP), Language.fromExtension(".cxx"));
            assertEquals(Optional.of(Language.CPP), Language.fromExtension(".cc"));
            assertEquals(Optional.of(Language.CPP), Language.fromExtension(".hpp"));
            assertEquals(Optional.of(Language.CPP), Language.fromExtension(".h"));

            assertEquals(Optional.empty(), Language.fromExtension(".unknown"));
            assertEquals(Optional.empty(), Language.fromExtension(""));
            assertEquals(Optional.empty(), Language.fromExtension("   "));
            assertEquals(Optional.empty(), Language.fromExtension(null));
        }

        @Test
        @DisplayName("Resolution from file name and full paths")
        void testFromFileName() {
            assertEquals(Optional.of(Language.JAVA), Language.fromFileName("Main.java"));
            assertEquals(Optional.of(Language.JAVA), Language.fromFileName("/path/to/App.JAVA"));
            assertEquals(Optional.of(Language.PYTHON), Language.fromFileName("script.py"));
            assertEquals(Optional.of(Language.CPP), Language.fromFileName("calculator.cpp"));
            assertEquals(Optional.of(Language.CPP), Language.fromFileName("header.hpp"));

            assertEquals(Optional.empty(), Language.fromFileName("Makefile"));
            assertEquals(Optional.empty(), Language.fromFileName("test."));
            assertEquals(Optional.empty(), Language.fromFileName(""));
            assertEquals(Optional.empty(), Language.fromFileName(null));
        }

        @Test
        @DisplayName("Resolution from identifier name, display name, or extension")
        void testFromNameOrExtension() {
            assertEquals(Optional.of(Language.JAVA), Language.fromNameOrExtension("JAVA"));
            assertEquals(Optional.of(Language.JAVA), Language.fromNameOrExtension("Java"));
            assertEquals(Optional.of(Language.PYTHON), Language.fromNameOrExtension("python"));
            assertEquals(Optional.of(Language.PYTHON), Language.fromNameOrExtension(".py"));
            assertEquals(Optional.of(Language.CPP), Language.fromNameOrExtension("C++"));
            assertEquals(Optional.of(Language.CPP), Language.fromNameOrExtension("cpp"));
            assertEquals(Optional.of(Language.CPP), Language.fromNameOrExtension("solution.cxx"));

            assertEquals(Optional.empty(), Language.fromNameOrExtension("rust"));
            assertEquals(Optional.empty(), Language.fromNameOrExtension(""));
            assertEquals(Optional.empty(), Language.fromNameOrExtension(null));
        }
    }

    @Nested
    @DisplayName("InputMode Enum Tests")
    class InputModeTests {

        @Test
        @DisplayName("Input modes contain required values and human-readable names")
        void testInputModes() {
            assertEquals(3, InputMode.values().length);
            assertEquals("Single Line", InputMode.SINGLE_LINE.displayName());
            assertEquals("Code Snippet", InputMode.CODE_SNIPPET.displayName());
            assertEquals("File Upload", InputMode.FILE_UPLOAD.displayName());
        }
    }

    @Nested
    @DisplayName("SourceLocation Tests")
    class SourceLocationTests {

        @Test
        @DisplayName("Valid SourceLocation creation and getters")
        void testValidLocation() {
            SourceLocation loc = SourceLocation.of(1, 1, 0);
            assertEquals(1, loc.line());
            assertEquals(1, loc.column());
            assertEquals(0, loc.charOffset());

            SourceLocation loc2 = SourceLocation.of(10, 5);
            assertEquals(10, loc2.line());
            assertEquals(5, loc2.column());
            assertEquals(0, loc2.charOffset());

            SourceLocation start = SourceLocation.start();
            assertEquals(1, start.line());
            assertEquals(1, start.column());
            assertEquals(0, start.charOffset());
            assertTrue(loc.toString().contains("1:1"));
        }

        @Test
        @DisplayName("Rejects invalid coordinate values")
        void testCoordinateInvariants() {
            assertThrows(IllegalArgumentException.class, () -> new SourceLocation(0, 1, 0));
            assertThrows(IllegalArgumentException.class, () -> new SourceLocation(-1, 1, 0));
            assertThrows(IllegalArgumentException.class, () -> new SourceLocation(1, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new SourceLocation(1, -5, 0));
            assertThrows(IllegalArgumentException.class, () -> new SourceLocation(1, 1, -1));
        }

        @Test
        @DisplayName("Comparable ordering checks line, column, then offset")
        void testComparison() {
            SourceLocation l1 = SourceLocation.of(1, 1, 0);
            SourceLocation l2 = SourceLocation.of(1, 2, 1);
            SourceLocation l3 = SourceLocation.of(2, 1, 10);
            SourceLocation l4 = SourceLocation.of(1, 1, 5);
            SourceLocation l1Copy = SourceLocation.of(1, 1, 0);

            assertEquals(0, l1.compareTo(l1Copy));
            assertTrue(l1.compareTo(l2) < 0);
            assertTrue(l2.compareTo(l1) > 0);
            assertTrue(l1.compareTo(l3) < 0);
            assertTrue(l3.compareTo(l2) > 0);
            assertTrue(l1.compareTo(l4) < 0);

            assertThrows(NullPointerException.class, () -> l1.compareTo(null));
        }
    }

    @Nested
    @DisplayName("SourceSpan Tests")
    class SourceSpanTests {

        @Test
        @DisplayName("Valid point and range span creation")
        void testValidSpan() {
            SourceLocation start = SourceLocation.of(1, 1, 0);
            SourceLocation end = SourceLocation.of(1, 5, 4);

            SourceSpan span = SourceSpan.of(start, end);
            assertEquals(start, span.start());
            assertEquals(end, span.end());
            assertEquals(4, span.length());
            assertFalse(span.isEmpty());

            SourceSpan point = SourceSpan.point(start);
            assertEquals(start, point.start());
            assertEquals(start, point.end());
            assertEquals(0, point.length());
            assertTrue(point.isEmpty());

            SourceSpan direct = SourceSpan.of(2, 3, 10, 2, 8, 15);
            assertEquals(2, direct.start().line());
            assertEquals(5, direct.length());
        }

        @Test
        @DisplayName("Span containment checks")
        void testSpanContains() {
            SourceSpan span = SourceSpan.of(
                SourceLocation.of(2, 5, 20),
                SourceLocation.of(2, 15, 30)
            );

            assertTrue(span.contains(SourceLocation.of(2, 5, 20)));
            assertTrue(span.contains(SourceLocation.of(2, 10, 25)));
            assertTrue(span.contains(SourceLocation.of(2, 15, 30)));

            assertFalse(span.contains(SourceLocation.of(1, 1, 0)));
            assertFalse(span.contains(SourceLocation.of(3, 1, 40)));

            assertTrue(span.contains(20));
            assertTrue(span.contains(25));
            assertTrue(span.contains(30));
            assertFalse(span.contains(19));
            assertFalse(span.contains(31));

            assertThrows(NullPointerException.class, () -> span.contains((SourceLocation) null));
        }

        @Test
        @DisplayName("Rejects inverted spans or null endpoints")
        void testSpanInvariants() {
            SourceLocation loc1 = SourceLocation.of(1, 5, 10);
            SourceLocation loc2 = SourceLocation.of(1, 1, 0);

            assertThrows(IllegalArgumentException.class, () -> new SourceSpan(loc1, loc2));
            assertThrows(NullPointerException.class, () -> new SourceSpan(null, loc1));
            assertThrows(NullPointerException.class, () -> new SourceSpan(loc1, null));

            // Start offset equal but line inverted
            SourceLocation locLine2 = SourceLocation.of(2, 1, 0);
            SourceLocation locLine1 = SourceLocation.of(1, 1, 0);
            assertThrows(IllegalArgumentException.class, () -> new SourceSpan(locLine2, locLine1));
        }
    }

    @Nested
    @DisplayName("SourcePayload Tests")
    class SourcePayloadTests {

        @Test
        @DisplayName("Constructs payload with valid inputs and helper metrics")
        void testPayload() {
            SourcePayload payload = SourcePayload.of("int x = 10;", "test.java", Language.JAVA, InputMode.SINGLE_LINE);
            assertEquals("int x = 10;", payload.sourceText());
            assertEquals("test.java", payload.sourceName());
            assertEquals(Language.JAVA, payload.language());
            assertEquals(InputMode.SINGLE_LINE, payload.inputMode());
            assertEquals(11, payload.characterCount());
            assertEquals(1, payload.lineCount());
        }

        @Test
        @DisplayName("Calculates lineCount correctly across single, multi-line, CRLF, and empty text")
        void testLineCount() {
            assertEquals(0, SourcePayload.snippet("", Language.PYTHON).lineCount());
            assertEquals(1, SourcePayload.snippet("print('hello')", Language.PYTHON).lineCount());
            assertEquals(2, SourcePayload.snippet("print('hello')\n", Language.PYTHON).lineCount());
            assertEquals(3, SourcePayload.snippet("a = 1\nb = 2\nc = 3", Language.PYTHON).lineCount());
            assertEquals(3, SourcePayload.snippet("a = 1\r\nb = 2\r\nc = 3", Language.PYTHON).lineCount());
            assertEquals(2, SourcePayload.snippet("a\rb", Language.CPP).lineCount());
            assertEquals(2, SourcePayload.snippet("a\r", Language.CPP).lineCount());
        }

        @Test
        @DisplayName("Factory methods create payloads with standard origin identifiers")
        void testFactories() {
            SourcePayload single = SourcePayload.singleLine("x = 1", Language.PYTHON);
            assertEquals(InputMode.SINGLE_LINE, single.inputMode());
            assertEquals("<single-line>", single.sourceName());

            SourcePayload snip = SourcePayload.snippet("x = 1", Language.PYTHON);
            assertEquals(InputMode.CODE_SNIPPET, snip.inputMode());
            assertEquals("<snippet>", snip.sourceName());

            SourcePayload file = SourcePayload.file("int main() {}", "main.cpp", Language.CPP);
            assertEquals(InputMode.FILE_UPLOAD, file.inputMode());
            assertEquals("main.cpp", file.sourceName());
        }

        @Test
        @DisplayName("Null arguments are strictly rejected")
        void testNullValidations() {
            assertThrows(NullPointerException.class, () -> new SourcePayload(null, "f", Language.JAVA, InputMode.SINGLE_LINE));
            assertThrows(NullPointerException.class, () -> new SourcePayload("code", null, Language.JAVA, InputMode.SINGLE_LINE));
            assertThrows(NullPointerException.class, () -> new SourcePayload("code", "f", null, InputMode.SINGLE_LINE));
            assertThrows(NullPointerException.class, () -> new SourcePayload("code", "f", Language.JAVA, null));
        }
    }

    @Nested
    @DisplayName("TokenType Enum Tests")
    class TokenTypeTests {

        @Test
        @DisplayName("Contains all 13 canonical token types")
        void testAllTokenTypes() {
            assertEquals(13, TokenType.values().length);
            assertTrue(TokenType.LITERAL_STRING.isLiteral());
            assertTrue(TokenType.LITERAL_CHAR.isLiteral());
            assertTrue(TokenType.LITERAL_NUMBER.isLiteral());
            assertFalse(TokenType.IDENTIFIER.isLiteral());

            assertTrue(TokenType.WHITESPACE.isTrivia());
            assertTrue(TokenType.COMMENT.isTrivia());
            assertTrue(TokenType.NEWLINE.isTrivia());
            assertFalse(TokenType.KEYWORD.isTrivia());
            assertFalse(TokenType.OPERATOR.isTrivia());
            assertFalse(TokenType.DELIMITER.isTrivia());
            assertFalse(TokenType.INDENT.isTrivia());
            assertFalse(TokenType.DEDENT.isTrivia());
            assertFalse(TokenType.UNKNOWN.isTrivia());
        }
    }

    @Nested
    @DisplayName("Token Record Tests")
    class TokenTests {

        @Test
        @DisplayName("Valid token construction and helpers")
        void testTokenCreation() {
            SourceSpan span = SourceSpan.of(1, 1, 0, 1, 6, 5);
            Token token = Token.of(TokenType.KEYWORD, "public", span, "ACCESS_MODIFIER");

            assertEquals(TokenType.KEYWORD, token.tokenType());
            assertEquals("public", token.lexeme());
            assertEquals(span, token.span());
            assertEquals("ACCESS_MODIFIER", token.category());
            assertEquals(SourceLocation.of(1, 1, 0), token.startLocation());
            assertEquals(SourceLocation.of(1, 6, 5), token.endLocation());

            Token defaultCategory = Token.of(TokenType.IDENTIFIER, "myVar", span);
            assertEquals("IDENTIFIER", defaultCategory.category());
        }

        @Test
        @DisplayName("Rejects null components in Token")
        void testNullTokenComponents() {
            SourceSpan span = SourceSpan.of(1, 1, 0, 1, 2, 1);
            assertThrows(NullPointerException.class, () -> new Token(null, "x", span, "CAT"));
            assertThrows(NullPointerException.class, () -> new Token(TokenType.IDENTIFIER, null, span, "CAT"));
            assertThrows(NullPointerException.class, () -> new Token(TokenType.IDENTIFIER, "x", null, "CAT"));
            assertThrows(NullPointerException.class, () -> new Token(TokenType.IDENTIFIER, "x", span, null));
        }
    }

    @Nested
    @DisplayName("SyntaxNode Tests")
    class SyntaxNodeTests {

        @Test
        @DisplayName("Leaf and composite node creation with immutability")
        void testSyntaxNodeCreation() {
            SourceSpan span = SourceSpan.of(1, 1, 0, 1, 10, 9);
            SyntaxNode leaf1 = SyntaxNode.leaf("Literal", "42", span);
            assertTrue(leaf1.isLeaf());
            assertEquals(0, leaf1.childCount());
            assertTrue(leaf1.children().isEmpty());
            assertTrue(leaf1.attributes().isEmpty());

            SyntaxNode leaf2 = SyntaxNode.leaf("Identifier", "x", span, Map.of("symbol", "variable"));
            assertEquals(Optional.of("variable"), leaf2.attribute("symbol"));
            assertEquals(Optional.empty(), leaf2.attribute("nonexistent"));

            SyntaxNode simpleNode = SyntaxNode.of("Simple", "lbl", span);
            assertTrue(simpleNode.isLeaf());
            assertTrue(simpleNode.children().isEmpty());
            assertTrue(simpleNode.attributes().isEmpty());

            SyntaxNode nullFallbackNode = new SyntaxNode("NullFallback", "lbl", span, null, null);
            assertTrue(nullFallbackNode.children().isEmpty());
            assertTrue(nullFallbackNode.attributes().isEmpty());

            List<SyntaxNode> mutableChildren = new ArrayList<>();
            mutableChildren.add(leaf1);
            mutableChildren.add(leaf2);

            Map<String, String> mutableAttrs = new HashMap<>();
            mutableAttrs.put("op", "+");

            SyntaxNode root = SyntaxNode.of("BinaryExpression", "+", span, mutableChildren, mutableAttrs);
            assertFalse(root.isLeaf());
            assertEquals(2, root.childCount());
            assertEquals("BinaryExpression", root.kind());
            assertEquals("+", root.label());

            // Verify defensive copies
            mutableChildren.clear();
            assertEquals(2, root.children().size());

            mutableAttrs.clear();
            assertEquals(Optional.of("+"), root.attribute("op"));

            // Verify children and attributes are unmodifiable
            assertThrows(UnsupportedOperationException.class, () -> root.children().add(leaf1));
            assertThrows(UnsupportedOperationException.class, () -> root.attributes().put("foo", "bar"));
        }

        @Test
        @DisplayName("Pre-order traversal visits all nodes in tree")
        void testPreorderTraversal() {
            SourceSpan span = SourceSpan.of(1, 1, 0, 1, 1, 0);
            SyntaxNode c1 = SyntaxNode.leaf("Child1", "c1", span);
            SyntaxNode c2 = SyntaxNode.leaf("Child2", "c2", span);
            SyntaxNode parent = SyntaxNode.of("Root", "root", span, List.of(c1, c2));

            List<String> visited = new ArrayList<>();
            parent.walkPreorder(node -> visited.add(node.kind()));

            assertEquals(List.of("Root", "Child1", "Child2"), visited);
            assertThrows(NullPointerException.class, () -> parent.walkPreorder(null));
        }

        @Test
        @DisplayName("Rejects null kind, label, or span")
        void testInvariants() {
            SourceSpan span = SourceSpan.of(1, 1, 0, 1, 1, 0);
            assertThrows(NullPointerException.class, () -> new SyntaxNode(null, "label", span, List.of(), Map.of()));
            assertThrows(NullPointerException.class, () -> new SyntaxNode("kind", null, span, List.of(), Map.of()));
            assertThrows(NullPointerException.class, () -> new SyntaxNode("kind", "label", null, List.of(), Map.of()));
        }
    }

    @Nested
    @DisplayName("Diagnostic and Summary Tests")
    class DiagnosticTests {

        @Test
        @DisplayName("CheckCategory values and display names")
        void testCheckCategories() {
            assertEquals(6, CheckCategory.values().length);
            assertEquals("Delimiter Match", CheckCategory.DELIMITER_MATCH.displayName());
            assertEquals("Literal Syntax", CheckCategory.LITERAL_SYNTAX.displayName());
            assertEquals("Statement Terminator", CheckCategory.STATEMENT_TERMINATOR.displayName());
            assertEquals("Operator Syntax", CheckCategory.OPERATOR_SYNTAX.displayName());
            assertEquals("Control Header", CheckCategory.CONTROL_HEADER.displayName());
            assertEquals("Identifier Naming", CheckCategory.IDENTIFIER_NAMING.displayName());
        }

        @Test
        @DisplayName("Severity values and predicates")
        void testSeverities() {
            assertEquals(3, Severity.values().length);
            assertTrue(Severity.ERROR.isError());
            assertFalse(Severity.ERROR.isWarning());
            assertFalse(Severity.ERROR.isInfo());

            assertTrue(Severity.WARNING.isWarning());
            assertTrue(Severity.INFO.isInfo());
        }

        @Test
        @DisplayName("Diagnostic record creation, factories, and validations")
        void testDiagnosticRecord() {
            SourceLocation loc = SourceLocation.of(3, 12, 45);
            Diagnostic err = Diagnostic.error(
                CheckCategory.STATEMENT_TERMINATOR,
                loc,
                "Missing semicolon",
                "ST001",
                ";"
            );
            assertTrue(err.isError());
            assertEquals(Severity.ERROR, err.severity());
            assertEquals(CheckCategory.STATEMENT_TERMINATOR, err.category());
            assertEquals("Missing semicolon", err.message());
            assertEquals("ST001", err.code());
            assertEquals(Optional.of(";"), err.suggestedFix());

            Diagnostic warn = Diagnostic.warning(
                CheckCategory.IDENTIFIER_NAMING,
                loc,
                "Non-standard variable casing",
                "IN002"
            );
            assertFalse(warn.isError());
            assertEquals(Optional.empty(), warn.suggestedFix());

            Diagnostic warnWithFix = Diagnostic.warning(
                CheckCategory.IDENTIFIER_NAMING,
                loc,
                "Non-standard variable casing",
                "IN002",
                "myVariable"
            );
            assertEquals(Optional.of("myVariable"), warnWithFix.suggestedFix());

            Diagnostic info = Diagnostic.info(
                CheckCategory.CONTROL_HEADER,
                loc,
                "Consider parentheses around condition",
                "CH003",
                "()"
            );
            assertEquals(Severity.INFO, info.severity());
            assertEquals(Optional.of("()"), info.suggestedFix());

            // Invariant null checks
            assertThrows(NullPointerException.class, () -> new Diagnostic(null, Severity.ERROR, loc, "m", "c", Optional.empty()));
            assertThrows(NullPointerException.class, () -> new Diagnostic(CheckCategory.DELIMITER_MATCH, null, loc, "m", "c", Optional.empty()));
            assertThrows(NullPointerException.class, () -> new Diagnostic(CheckCategory.DELIMITER_MATCH, Severity.ERROR, null, "m", "c", Optional.empty()));
            assertThrows(NullPointerException.class, () -> new Diagnostic(CheckCategory.DELIMITER_MATCH, Severity.ERROR, loc, null, "c", Optional.empty()));
            assertThrows(NullPointerException.class, () -> new Diagnostic(CheckCategory.DELIMITER_MATCH, Severity.ERROR, loc, "m", null, Optional.empty()));
            assertThrows(NullPointerException.class, () -> new Diagnostic(CheckCategory.DELIMITER_MATCH, Severity.ERROR, loc, "m", "c", null));
        }

        @Test
        @DisplayName("AnalysisStatus predicates")
        void testAnalysisStatus() {
            assertTrue(AnalysisStatus.PASSED.isPassed());
            assertFalse(AnalysisStatus.PASSED.hasErrors());

            assertFalse(AnalysisStatus.FAILED_SYNTAX_ERRORS.isPassed());
            assertTrue(AnalysisStatus.FAILED_SYNTAX_ERRORS.hasErrors());
        }

        @Test
        @DisplayName("DiagnosticSummary calculate with clean input produces PASSED")
        void testSummaryCleanInput() {
            DiagnosticSummary summary = DiagnosticSummary.calculate(10, 25, List.of());
            assertEquals(10, summary.totalLines());
            assertEquals(25, summary.totalTokens());
            assertEquals(0, summary.errorCount());
            assertEquals(0, summary.warningCount());
            assertEquals(AnalysisStatus.PASSED, summary.status());
            assertTrue(summary.isPassed());
            assertFalse(summary.hasErrors());

            for (CheckCategory cat : CheckCategory.values()) {
                assertEquals(0, summary.countForCategory(cat));
            }
        }

        @Test
        @DisplayName("DiagnosticSummary calculate with errors produces FAILED_SYNTAX_ERRORS and category breakdown")
        void testSummaryWithErrors() {
            SourceLocation loc = SourceLocation.of(1, 1, 0);
            List<Diagnostic> diagnostics = List.of(
                Diagnostic.error(CheckCategory.DELIMITER_MATCH, loc, "Unclosed bracket", "DM001"),
                Diagnostic.error(CheckCategory.STATEMENT_TERMINATOR, loc, "Missing semicolon", "ST001"),
                Diagnostic.warning(CheckCategory.IDENTIFIER_NAMING, loc, "Name style warning", "IN001"),
                Diagnostic.info(CheckCategory.CONTROL_HEADER, loc, "Information", "CH001")
            );

            DiagnosticSummary summary = DiagnosticSummary.calculate(5, 12, diagnostics);
            assertEquals(5, summary.totalLines());
            assertEquals(12, summary.totalTokens());
            assertEquals(2, summary.errorCount());
            assertEquals(1, summary.warningCount());
            assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, summary.status());
            assertFalse(summary.isPassed());
            assertTrue(summary.hasErrors());

            assertEquals(1, summary.countForCategory(CheckCategory.DELIMITER_MATCH));
            assertEquals(1, summary.countForCategory(CheckCategory.STATEMENT_TERMINATOR));
            assertEquals(1, summary.countForCategory(CheckCategory.IDENTIFIER_NAMING));
            assertEquals(1, summary.countForCategory(CheckCategory.CONTROL_HEADER));
            assertEquals(0, summary.countForCategory(CheckCategory.LITERAL_SYNTAX));
            assertEquals(0, summary.countForCategory(CheckCategory.OPERATOR_SYNTAX));
        }

        @Test
        @DisplayName("DiagnosticSummary calculate with only warnings produces PASSED status")
        void testSummaryWithOnlyWarnings() {
            SourceLocation loc = SourceLocation.of(1, 1, 0);
            List<Diagnostic> diagnostics = List.of(
                Diagnostic.warning(CheckCategory.IDENTIFIER_NAMING, loc, "Name style warning", "IN001")
            );

            DiagnosticSummary summary = DiagnosticSummary.calculate(1, 5, diagnostics);
            assertEquals(0, summary.errorCount());
            assertEquals(1, summary.warningCount());
            assertEquals(AnalysisStatus.PASSED, summary.status());
        }

        @Test
        @DisplayName("DiagnosticSummary invariants rejection")
        void testSummaryInvariants() {
            Map<CheckCategory, Integer> counts = Map.of(CheckCategory.DELIMITER_MATCH, 0);
            assertThrows(IllegalArgumentException.class, () -> new DiagnosticSummary(-1, 0, 0, 0, counts, AnalysisStatus.PASSED));
            assertThrows(IllegalArgumentException.class, () -> new DiagnosticSummary(0, -1, 0, 0, counts, AnalysisStatus.PASSED));
            assertThrows(IllegalArgumentException.class, () -> new DiagnosticSummary(0, 0, -1, 0, counts, AnalysisStatus.PASSED));
            assertThrows(IllegalArgumentException.class, () -> new DiagnosticSummary(0, 0, 0, -1, counts, AnalysisStatus.PASSED));
            assertThrows(NullPointerException.class, () -> new DiagnosticSummary(0, 0, 0, 0, null, AnalysisStatus.PASSED));
            assertThrows(NullPointerException.class, () -> new DiagnosticSummary(0, 0, 0, 0, counts, null));

            // calculate guards
            assertThrows(IllegalArgumentException.class, () -> DiagnosticSummary.calculate(-1, 0, List.of()));
            assertThrows(IllegalArgumentException.class, () -> DiagnosticSummary.calculate(0, -1, List.of()));

            // calculate with null in list ignores null entry safely
            List<Diagnostic> withNull = new ArrayList<>();
            withNull.add(null);
            withNull.add(Diagnostic.error(CheckCategory.LITERAL_SYNTAX, SourceLocation.start(), "err", "E"));
            DiagnosticSummary summaryWithNull = DiagnosticSummary.calculate(1, 1, withNull);
            assertEquals(1, summaryWithNull.errorCount());

            // calculate with null diagnostics list
            DiagnosticSummary nullListSummary = DiagnosticSummary.calculate(10, 5, null);
            assertEquals(0, nullListSummary.errorCount());
            assertEquals(AnalysisStatus.PASSED, nullListSummary.status());

            // calculate with payload and null tokens list
            SourcePayload payload = SourcePayload.singleLine("int x = 1;", Language.JAVA);
            DiagnosticSummary fromPayloadNullTokens = DiagnosticSummary.calculate(payload, null, List.of());
            assertEquals(1, fromPayloadNullTokens.totalLines());
            assertEquals(0, fromPayloadNullTokens.totalTokens());
        }
    }

    @Nested
    @DisplayName("AnalysisResult Aggregate Tests")
    class AnalysisResultTests {

        @Test
        @DisplayName("Full aggregate construction and auto-summary calculation")
        void testAnalysisResultAutoCalculation() {
            SourcePayload payload = SourcePayload.snippet("int x = 1;\nint y = 2;", Language.JAVA);
            SourceSpan span = SourceSpan.of(1, 1, 0, 1, 4, 3);
            List<Token> tokens = List.of(
                Token.of(TokenType.KEYWORD, "int", span),
                Token.of(TokenType.IDENTIFIER, "x", span)
            );
            SyntaxNode ast = SyntaxNode.leaf("CompilationUnit", "main", span);
            List<Diagnostic> diagnostics = List.of(
                Diagnostic.error(CheckCategory.STATEMENT_TERMINATOR, SourceLocation.of(1, 10, 9), "Missing semicolon", "ST001")
            );

            AnalysisResult result = AnalysisResult.of(payload, tokens, Optional.of(ast), diagnostics);

            assertEquals(payload, result.payload());
            assertEquals(tokens, result.tokens());
            assertTrue(result.syntaxTree().isPresent());
            assertEquals(ast, result.syntaxTree().get());
            assertEquals(diagnostics, result.diagnostics());

            // Test 4-arg factory with raw SyntaxNode
            AnalysisResult rawTreeResult = AnalysisResult.of(payload, tokens, ast, diagnostics);
            assertTrue(rawTreeResult.syntaxTree().isPresent());
            assertEquals(ast, rawTreeResult.syntaxTree().get());

            // Summary verify
            DiagnosticSummary summary = result.summary();
            assertEquals(2, summary.totalLines());
            assertEquals(2, summary.totalTokens());
            assertEquals(1, summary.errorCount());
            assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, summary.status());

            assertTrue(result.hasErrors());
            assertFalse(result.isPassed());
            assertEquals(1, result.errorCount());
            assertEquals(0, result.warningCount());
        }

        @Test
        @DisplayName("Convenience factories and defensive copies")
        void testConvenienceFactoriesAndDefensiveCopies() {
            SourcePayload payload = SourcePayload.singleLine("x = 1", Language.PYTHON);
            List<Token> mutableTokens = new ArrayList<>();
            mutableTokens.add(Token.of(TokenType.IDENTIFIER, "x", SourceSpan.point(SourceLocation.start())));

            List<Diagnostic> mutableDiagnostics = new ArrayList<>();

            AnalysisResult result = AnalysisResult.of(payload, mutableTokens, mutableDiagnostics);

            assertTrue(result.syntaxTree().isEmpty());
            assertTrue(result.isPassed());
            assertEquals(0, result.errorCount());

            // Defensively copied
            mutableTokens.clear();
            assertEquals(1, result.tokens().size());

            assertThrows(UnsupportedOperationException.class, () -> result.tokens().add(
                Token.of(TokenType.KEYWORD, "def", SourceSpan.point(SourceLocation.start()))
            ));
            assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().add(
                Diagnostic.error(CheckCategory.DELIMITER_MATCH, SourceLocation.start(), "err", "E")
            ));

            // Constructor null tokens / diagnostics fallback
            AnalysisResult nullCollections = new AnalysisResult(
                payload,
                null,
                Optional.empty(),
                null,
                DiagnosticSummary.calculate(1, 0, List.of())
            );
            assertTrue(nullCollections.tokens().isEmpty());
            assertTrue(nullCollections.diagnostics().isEmpty());
        }

        @Test
        @DisplayName("Rejects null payload, syntaxTree optional, or summary")
        void testNullValidations() {
            SourcePayload payload = SourcePayload.singleLine("x", Language.PYTHON);
            DiagnosticSummary summary = DiagnosticSummary.calculate(1, 1, List.of());

            assertThrows(NullPointerException.class, () -> new AnalysisResult(null, List.of(), Optional.empty(), List.of(), summary));
            assertThrows(NullPointerException.class, () -> new AnalysisResult(payload, List.of(), null, List.of(), summary));
            assertThrows(NullPointerException.class, () -> new AnalysisResult(payload, List.of(), Optional.empty(), List.of(), null));
        }
    }
}
