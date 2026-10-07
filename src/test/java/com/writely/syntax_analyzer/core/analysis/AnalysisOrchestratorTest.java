package com.writely.syntax_analyzer.core.analysis;

import com.writely.syntax_analyzer.adapter.ParseResult;
import com.writely.syntax_analyzer.adapter.ParserAdapter;
import com.writely.syntax_analyzer.adapter.ParserAdapterRegistry;
import com.writely.syntax_analyzer.adapter.RuleCapabilities;
import com.writely.syntax_analyzer.core.check.SyntaxChecker;
import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.AnalysisStatus;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.InputMode;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Analysis Orchestrator Tests")
class AnalysisOrchestratorTest {

    private static Diagnostic diagnostic(CheckCategory category, int line, int column, String code, String message) {
        return Diagnostic.of(category, Severity.ERROR, SourceLocation.of(line, column), message, code);
    }

    private static List<String> codes(AnalysisResult result) {
        List<String> codes = new ArrayList<>();
        for (Diagnostic d : result.diagnostics()) {
            codes.add(d.code());
        }
        return codes;
    }

    private static long countOfCategory(AnalysisResult result, CheckCategory category) {
        return result.diagnostics().stream().filter(d -> d.category() == category).count();
    }

    private static final class StubChecker implements SyntaxChecker {
        private final CheckCategory category;
        private final List<Diagnostic> findings;
        private final RuntimeException failure;

        private StubChecker(CheckCategory category, List<Diagnostic> findings, RuntimeException failure) {
            this.category = category;
            this.findings = findings;
            this.failure = failure;
        }

        static StubChecker emitting(CheckCategory category, Diagnostic... diagnostics) {
            return new StubChecker(category, List.of(diagnostics), null);
        }

        static StubChecker throwing(CheckCategory category, RuntimeException failure) {
            return new StubChecker(category, List.of(), failure);
        }

        static StubChecker silent(CheckCategory category) {
            return new StubChecker(category, List.of(), null);
        }

        @Override
        public CheckCategory category() {
            return category;
        }

        @Override
        public List<Diagnostic> check(SourcePayload payload, List<Token> tokens) {
            if (failure != null) {
                throw failure;
            }
            return findings;
        }
    }

    /**
     * Python adapter stub whose rule capabilities declare only the given categories.
     */
    private static class RestrictedAdapter implements ParserAdapter {
        private final RuleCapabilities capabilities;

        RestrictedAdapter(CheckCategory... supported) {
            this.capabilities = RuleCapabilities.builder()
                .supportedCategories(Set.of(supported))
                .build();
        }

        @Override
        public Language language() {
            return Language.PYTHON;
        }

        @Override
        public RuleCapabilities ruleCapabilities() {
            return capabilities;
        }

        @Override
        public ParseResult parse(SourcePayload payload, List<Token> tokens) {
            return ParseResult.ofTokens(tokens);
        }
    }

    private static final class TokenizeFailingAdapter extends RestrictedAdapter {
        private TokenizeFailingAdapter() {
            super(CheckCategory.values());
        }

        @Override
        public List<Token> tokenize(SourcePayload payload) {
            throw new IllegalStateException("tokenize exploded");
        }
    }

    private static final class ParseFailingAdapter extends RestrictedAdapter {
        private ParseFailingAdapter() {
            super(CheckCategory.values());
        }

        @Override
        public ParseResult parse(SourcePayload payload, List<Token> tokens) {
            throw new IllegalStateException("parse exploded");
        }
    }

    @Nested
    @DisplayName("Rule capability gating")
    class CapabilityGatingTests {

        @Test
        @DisplayName("A checker for an unsupported category contributes zero diagnostics")
        void unsupportedCategoryContributesZeroDiagnostics() {
            ParserAdapterRegistry registry = ParserAdapterRegistry.empty();
            registry.register(new RestrictedAdapter(CheckCategory.DELIMITER_MATCH));

            Diagnostic allowed = diagnostic(CheckCategory.DELIMITER_MATCH, 2, 4, "ERR_DELIM", "unclosed '('");
            Diagnostic blocked = diagnostic(CheckCategory.LITERAL_SYNTAX, 3, 11, "ERR_LIT", "unclosed '\"'");
            AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(registry, List.of(
                StubChecker.emitting(CheckCategory.DELIMITER_MATCH, allowed),
                StubChecker.emitting(CheckCategory.LITERAL_SYNTAX, blocked)
            ));

            SourcePayload payload = SourcePayload.snippet("x = 10\nprint(\"hi)", Language.PYTHON);
            AnalysisResult result = orchestrator.analyze(payload);

            assertEquals(1, result.diagnostics().size());
            assertEquals("ERR_DELIM", result.diagnostics().get(0).code());
            assertEquals(0, countOfCategory(result, CheckCategory.LITERAL_SYNTAX));
            assertEquals(0, result.summary().countForCategory(CheckCategory.LITERAL_SYNTAX));
            assertEquals(1, result.summary().countForCategory(CheckCategory.DELIMITER_MATCH));
        }

        @Test
        @DisplayName("Every declared category runs; undeclared categories are skipped")
        void declaredCategoriesRun() {
            ParserAdapterRegistry registry = ParserAdapterRegistry.empty();
            registry.register(new RestrictedAdapter(
                CheckCategory.DELIMITER_MATCH, CheckCategory.OPERATOR_SYNTAX));

            AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(registry, List.of(
                StubChecker.emitting(CheckCategory.DELIMITER_MATCH, diagnostic(CheckCategory.DELIMITER_MATCH, 1, 1, "ERR_A", "a")),
                StubChecker.emitting(CheckCategory.OPERATOR_SYNTAX, diagnostic(CheckCategory.OPERATOR_SYNTAX, 1, 5, "ERR_B", "b")),
                StubChecker.emitting(CheckCategory.CONTROL_HEADER, diagnostic(CheckCategory.CONTROL_HEADER, 1, 9, "ERR_C", "c")),
                StubChecker.emitting(CheckCategory.IDENTIFIER_NAMING, diagnostic(CheckCategory.IDENTIFIER_NAMING, 2, 1, "ERR_D", "d")),
                StubChecker.emitting(CheckCategory.STATEMENT_TERMINATOR, diagnostic(CheckCategory.STATEMENT_TERMINATOR, 2, 5, "ERR_E", "e"))
            ));

            AnalysisResult result = orchestrator.analyze(SourcePayload.snippet("x = 10", Language.PYTHON));

            assertEquals(List.of("ERR_A", "ERR_B"), codes(result));
            assertEquals(0, countOfCategory(result, CheckCategory.CONTROL_HEADER));
            assertEquals(0, countOfCategory(result, CheckCategory.IDENTIFIER_NAMING));
            assertEquals(0, countOfCategory(result, CheckCategory.STATEMENT_TERMINATOR));
        }

        @Test
        @DisplayName("Merged findings are sorted by (line, column, category ordinal, code)")
        void mergedFindingsAreSorted() {
            ParserAdapterRegistry registry = ParserAdapterRegistry.empty();
            registry.register(new RestrictedAdapter(CheckCategory.values()));

            AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(registry, List.of(
                StubChecker.emitting(CheckCategory.OPERATOR_SYNTAX,
                    diagnostic(CheckCategory.OPERATOR_SYNTAX, 3, 1, "ERR_Z", "later line")),
                StubChecker.emitting(CheckCategory.DELIMITER_MATCH,
                    diagnostic(CheckCategory.DELIMITER_MATCH, 1, 7, "ERR_A", "late column")),
                StubChecker.emitting(CheckCategory.CONTROL_HEADER,
                    diagnostic(CheckCategory.CONTROL_HEADER, 1, 7, "ERR_A", "same spot other category")),
                StubChecker.emitting(CheckCategory.IDENTIFIER_NAMING,
                    diagnostic(CheckCategory.IDENTIFIER_NAMING, 1, 2, "ERR_B", "early column"))
            ));

            AnalysisResult result = orchestrator.analyze(SourcePayload.snippet("x = 10", Language.PYTHON));

            assertEquals(
                List.of(
                    diagnostic(CheckCategory.IDENTIFIER_NAMING, 1, 2, "ERR_B", "early column"),
                    diagnostic(CheckCategory.DELIMITER_MATCH, 1, 7, "ERR_A", "late column"),
                    diagnostic(CheckCategory.CONTROL_HEADER, 1, 7, "ERR_A", "same spot other category"),
                    diagnostic(CheckCategory.OPERATOR_SYNTAX, 3, 1, "ERR_Z", "later line")
                ),
                result.diagnostics()
            );
        }
    }

    @Nested
    @DisplayName("Internal failure handling")
    class InternalFailureTests {

        @Test
        @DisplayName("A throwing checker yields ERR_INTERNAL_CHECK_FAILURE and no escaped exception")
        void throwingCheckerYieldsInternalFailureDiagnostic() {
            AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(
                ParserAdapterRegistry.createDefault(),
                List.of(
                    StubChecker.throwing(CheckCategory.OPERATOR_SYNTAX, new IllegalStateException("boom")),
                    StubChecker.emitting(CheckCategory.IDENTIFIER_NAMING,
                        diagnostic(CheckCategory.IDENTIFIER_NAMING, 1, 1, "ERR_ID", "bad name"))
                ));

            SourcePayload payload = SourcePayload.snippet("x = 10", Language.PYTHON);
            AnalysisResult result = assertDoesNotThrow(() -> orchestrator.analyze(payload));

            assertEquals(payload, result.payload());
            List<Diagnostic> internal = result.diagnostics().stream()
                .filter(d -> AnalysisOrchestrator.ERR_INTERNAL_CHECK_FAILURE.equals(d.code()))
                .toList();
            assertEquals(1, internal.size());
            Diagnostic failure = internal.get(0);
            assertEquals(Severity.ERROR, failure.severity());
            assertEquals(CheckCategory.OPERATOR_SYNTAX, failure.category());
            assertTrue(failure.message().contains("boom"), failure.message());
            assertEquals(SourceLocation.start(), failure.location());

            // other checkers still ran and the other finding survived
            assertEquals(1, countOfCategory(result, CheckCategory.IDENTIFIER_NAMING));
            assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        }

        @Test
        @DisplayName("Missing adapter resolution yields a complete result, not an exception")
        void missingAdapterYieldsInternalFailureDiagnostic() {
            AnalysisOrchestrator orchestrator =
                new AnalysisOrchestrator(ParserAdapterRegistry.empty(), List.of(StubChecker.silent(CheckCategory.DELIMITER_MATCH)));

            SourcePayload payload = SourcePayload.singleLine("x = 10", Language.PYTHON);
            AnalysisResult result = assertDoesNotThrow(() -> orchestrator.analyze(payload));

            assertEquals(payload, result.payload());
            assertTrue(result.tokens().isEmpty());
            assertEquals(1, result.diagnostics().size());
            Diagnostic failure = result.diagnostics().get(0);
            assertEquals(AnalysisOrchestrator.ERR_INTERNAL_CHECK_FAILURE, failure.code());
            assertEquals(CheckCategory.DELIMITER_MATCH, failure.category());
            assertEquals(Severity.ERROR, failure.severity());
            assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        }

        @Test
        @DisplayName("Tokenization failure short-circuits to a complete result")
        void tokenizationFailureYieldsInternalFailureDiagnostic() {
            ParserAdapterRegistry registry = ParserAdapterRegistry.empty();
            registry.register(new TokenizeFailingAdapter());
            AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(registry, List.of(
                StubChecker.emitting(CheckCategory.DELIMITER_MATCH,
                    diagnostic(CheckCategory.DELIMITER_MATCH, 1, 1, "ERR_DELIM", "should not run"))
            ));

            AnalysisResult result = assertDoesNotThrow(
                () -> orchestrator.analyze(SourcePayload.snippet("x = 10", Language.PYTHON)));

            assertTrue(result.tokens().isEmpty());
            assertEquals(1, result.diagnostics().size());
            Diagnostic failure = result.diagnostics().get(0);
            assertEquals(AnalysisOrchestrator.ERR_INTERNAL_CHECK_FAILURE, failure.code());
            assertEquals(CheckCategory.DELIMITER_MATCH, failure.category());
            assertTrue(failure.message().contains("tokenization"), failure.message());
            assertTrue(failure.message().contains("tokenize exploded"), failure.message());
        }

        @Test
        @DisplayName("Parsing failure still runs the checkers and returns a result")
        void parsingFailureStillRunsCheckers() {
            ParserAdapterRegistry registry = ParserAdapterRegistry.empty();
            registry.register(new ParseFailingAdapter());
            AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(registry, List.of(
                StubChecker.emitting(CheckCategory.DELIMITER_MATCH,
                    diagnostic(CheckCategory.DELIMITER_MATCH, 1, 3, "ERR_DELIM", "unclosed"))
            ));

            AnalysisResult result = assertDoesNotThrow(
                () -> orchestrator.analyze(SourcePayload.snippet("x = 10", Language.PYTHON)));

            assertFalse(result.tokens().isEmpty());
            assertEquals(2, result.diagnostics().size());
            List<String> codes = codes(result);
            assertTrue(codes.contains(AnalysisOrchestrator.ERR_INTERNAL_CHECK_FAILURE));
            assertTrue(codes.contains("ERR_DELIM"));
            result.diagnostics().stream()
                .filter(d -> AnalysisOrchestrator.ERR_INTERNAL_CHECK_FAILURE.equals(d.code()))
                .forEach(d -> {
                    assertEquals(CheckCategory.DELIMITER_MATCH, d.category());
                    assertTrue(d.message().contains("parsing"), d.message());
                });
        }

        @Test
        @DisplayName("Duplicate internal failures are de-duplicated like any other finding")
        void duplicateInternalFailuresAreDeduplicated() {
            ParserAdapterRegistry registry = ParserAdapterRegistry.empty();
            registry.register(new RestrictedAdapter(CheckCategory.values()));
            AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(registry, List.of(
                StubChecker.throwing(CheckCategory.OPERATOR_SYNTAX, new IllegalStateException("shared cause")),
                StubChecker.throwing(CheckCategory.CONTROL_HEADER, new IllegalStateException("shared cause"))
            ));

            // different categories -> both internal failures survive dedup
            AnalysisResult result = orchestrator.analyze(SourcePayload.snippet("x = 10", Language.PYTHON));
            assertEquals(2, result.diagnostics().size());
            assertEquals(1, countOfCategory(result, CheckCategory.OPERATOR_SYNTAX));
            assertEquals(1, countOfCategory(result, CheckCategory.CONTROL_HEADER));

            // identical internal failure from two checkers of the same category collapses
            AnalysisOrchestrator sameCategory = new AnalysisOrchestrator(registry, List.of(
                StubChecker.throwing(CheckCategory.OPERATOR_SYNTAX, new IllegalStateException("shared cause")),
                StubChecker.throwing(CheckCategory.OPERATOR_SYNTAX, new IllegalStateException("shared cause"))
            ));
            AnalysisResult collapsed = sameCategory.analyze(SourcePayload.snippet("x = 10", Language.PYTHON));
            assertEquals(1, countOfCategory(collapsed, CheckCategory.OPERATOR_SYNTAX));
        }
    }

    @Nested
    @DisplayName("Input mode and language coverage")
    class InputModeAndLanguageCoverageTests {

        private record Fixture(String label, SourcePayload payload) {
        }

        private List<Fixture> fixtures() {
            List<Fixture> fixtures = new ArrayList<>();
            fixtures.add(new Fixture("java single line", SourcePayload.singleLine("int x = 1;", Language.JAVA)));
            fixtures.add(new Fixture("java snippet",
                SourcePayload.snippet("class A { int x = 1; }", Language.JAVA)));
            fixtures.add(new Fixture("java file upload",
                SourcePayload.file("public class B { public static void main(String[] a) { } }",
                    "B.java", Language.JAVA)));

            fixtures.add(new Fixture("python single line", SourcePayload.singleLine("x = 10", Language.PYTHON)));
            fixtures.add(new Fixture("python snippet",
                SourcePayload.snippet("def f(a):\n    return a + 1", Language.PYTHON)));
            fixtures.add(new Fixture("python file upload",
                SourcePayload.file("x = 10\ny = 20", "script.py", Language.PYTHON)));

            fixtures.add(new Fixture("cpp single line", SourcePayload.singleLine("int x = 1;", Language.CPP)));
            fixtures.add(new Fixture("cpp snippet",
                SourcePayload.snippet("int main() { return 0; }", Language.CPP)));
            fixtures.add(new Fixture("cpp file upload",
                SourcePayload.file("#include <iostream>\nint main() { return 0; }", "main.cpp", Language.CPP)));
            return fixtures;
        }

        @Test
        @DisplayName("All three input modes in all three languages return a complete AnalysisResult")
        void allInputModesAndLanguagesProduceResults() {
            AnalysisOrchestrator orchestrator = new AnalysisOrchestrator();
            List<Fixture> fixtures = fixtures();

            assertEquals(9, fixtures.size());
            Set<InputMode> seenModes = new java.util.HashSet<>();
            Set<Language> seenLanguages = new java.util.HashSet<>();

            for (Fixture fixture : fixtures) {
                AnalysisResult result = assertDoesNotThrow(() -> orchestrator.analyze(fixture.payload()),
                    () -> "analyze must not throw for " + fixture.label());

                assertNotNull(result, fixture.label());
                assertSame(fixture.payload(), result.payload(), fixture.label());
                assertFalse(result.tokens().isEmpty(), fixture.label());
                assertTrue(result.diagnostics().isEmpty(), fixture.label());
                assertNotNull(result.summary(), fixture.label());
                assertEquals(fixture.payload().lineCount(), result.summary().totalLines(), fixture.label());
                assertEquals(0, result.summary().flaggedLines(), fixture.label());
                assertEquals(result.summary().totalLines(), result.summary().validLines(), fixture.label());
                assertEquals(AnalysisStatus.PASSED, result.summary().status(), fixture.label());
                assertTrue(result.isPassed(), fixture.label());

                seenModes.add(fixture.payload().inputMode());
                seenLanguages.add(fixture.payload().language());
            }

            assertEquals(Set.of(InputMode.SINGLE_LINE, InputMode.CODE_SNIPPET, InputMode.FILE_UPLOAD), seenModes);
            assertEquals(Set.of(Language.JAVA, Language.PYTHON, Language.CPP), seenLanguages);
        }

        @Test
        @DisplayName("Default orchestrator runs the full six-checker suite")
        void defaultOrchestratorRunsAllSixCheckers() {
            assertEquals(6, AnalysisOrchestrator.defaultCheckers().size());
            Set<CheckCategory> categories = new java.util.HashSet<>();
            for (SyntaxChecker checker : AnalysisOrchestrator.defaultCheckers()) {
                assertTrue(categories.add(checker.category()), "duplicate checker category: " + checker.category());
            }
            assertEquals(Set.of(CheckCategory.values()), categories);
        }
    }
}
