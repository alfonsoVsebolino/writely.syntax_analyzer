package com.writely.syntax_analyzer.regression;

import com.writely.syntax_analyzer.adapter.JavaAdapter;
import com.writely.syntax_analyzer.adapter.ParseResult;
import com.writely.syntax_analyzer.core.analysis.AnalysisOrchestrator;
import com.writely.syntax_analyzer.core.check.DelimiterMatchingChecker;
import com.writely.syntax_analyzer.core.tokenization.Tokenizer;
import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.AnalysisStatus;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-language regression suite for Java (issue #12).
 *
 * <p>Runs the full {@link AnalysisOrchestrator} pipeline over file fixtures in
 * all three input modes, asserting strict structural parity (exact code,
 * category, 1-based line/column, {@link Severity#ERROR}) for invalid fixtures
 * and clean passes for valid fixtures.</p>
 */
@DisplayName("Java Regression Tests (Issue #12)")
class JavaRegressionTest {

    private AnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AnalysisOrchestrator();
    }

    private String loadFixture(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    private static void assertContains(AnalysisResult result, CheckCategory category,
                                       String code, int line, int column) {
        boolean found = result.diagnostics().stream().anyMatch(d ->
            d.category() == category
                && d.code().equals(code)
                && d.location().line() == line
                && d.location().column() == column
                && d.severity() == Severity.ERROR);
        assertTrue(found, () -> "Expected " + category + "/" + code
            + " at L" + line + "C" + column + " but got: " + result.diagnostics());
    }

    private void assertValidAcrossModes(String code, String fileName) {
        List<SourcePayload> payloads = List.of(
            SourcePayload.singleLine(code, Language.JAVA),
            SourcePayload.snippet(code, Language.JAVA),
            SourcePayload.file(code, fileName, Language.JAVA)
        );
        for (SourcePayload payload : payloads) {
            AnalysisResult result = orchestrator.analyze(payload);
            assertEquals(AnalysisStatus.PASSED, result.summary().status(),
                () -> "Expected PASSED for " + payload.inputMode() + " but got: " + result.diagnostics());
            assertTrue(result.diagnostics().isEmpty(),
                () -> "Expected zero diagnostics for " + payload.inputMode() + " but got: " + result.diagnostics());
            assertEquals(0, result.summary().errorCount());
            assertEquals(0, result.summary().warningCount());
            assertTrue(result.isPassed());
        }
    }

    @Test
    @DisplayName("Valid CompleteProgram passes in all three input modes")
    void validCompleteProgramAllModes() {
        String code = loadFixture("/fixtures/java/valid/CompleteProgram.java");
        assertValidAcrossModes(code, "CompleteProgram.java");
    }

    @Test
    @DisplayName("Valid Snippet passes in all three input modes")
    void validSnippetAllModes() {
        String code = loadFixture("/fixtures/java/valid/Snippet.java");
        assertValidAcrossModes(code, "Snippet.java");
    }

    @Test
    @DisplayName("InvalidDelimiters: mismatched (] at L5C23")
    void invalidDelimiters() {
        String code = loadFixture("/fixtures/java/invalid/InvalidDelimiters.java");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidDelimiters.java", Language.JAVA));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.DELIMITER_MATCH, "ERR_MISMATCHED_DELIMITER", 5, 23);
    }

    @Test
    @DisplayName("InvalidLiterals: unclosed string at L5C20")
    void invalidLiterals() {
        String code = loadFixture("/fixtures/java/invalid/InvalidLiterals.java");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidLiterals.java", Language.JAVA));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.LITERAL_SYNTAX, "ERR_UNCLOSED_STRING_LITERAL", 5, 20);
    }

    @Test
    @DisplayName("InvalidTerminatorsAndBlocks: missing semicolon at L5C19")
    void invalidTerminatorsAndBlocks() {
        String code = loadFixture("/fixtures/java/invalid/InvalidTerminatorsAndBlocks.java");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidTerminatorsAndBlocks.java", Language.JAVA));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.STATEMENT_TERMINATOR, "ERR_MISSING_SEMICOLON", 5, 19);
    }

    @Test
    @DisplayName("InvalidOperators: consecutive '+ *' at L5C22")
    void invalidOperators() {
        String code = loadFixture("/fixtures/java/invalid/InvalidOperators.java");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidOperators.java", Language.JAVA));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.OPERATOR_SYNTAX, "ERR_INVALID_OPERATOR_SEQUENCE", 5, 22);
    }

    @Test
    @DisplayName("InvalidControlHeaders: missing '(' at L5C15")
    void invalidControlHeaders() {
        String code = loadFixture("/fixtures/java/invalid/InvalidControlHeaders.java");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidControlHeaders.java", Language.JAVA));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.CONTROL_HEADER, "ERR_MISSING_HEADER_DELIMITER", 5, 15);
    }

    @Test
    @DisplayName("InvalidIdentifiers: keyword-as-identifier at L5C13")
    void invalidIdentifiers() {
        String code = loadFixture("/fixtures/java/invalid/InvalidIdentifiers.java");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidIdentifiers.java", Language.JAVA));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.IDENTIFIER_NAMING, "ERR_KEYWORD_AS_IDENTIFIER", 5, 13);
    }

    @Test
    @DisplayName("MultiErrorRecovery: three independent errors, no cascading false positives")
    void multiErrorRecovery() {
        String code = loadFixture("/fixtures/java/invalid/MultiErrorRecovery.java");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "MultiErrorRecovery.java", Language.JAVA));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());

        assertContains(result, CheckCategory.STATEMENT_TERMINATOR, "ERR_MISSING_SEMICOLON", 5, 19);
        assertContains(result, CheckCategory.OPERATOR_SYNTAX, "ERR_INVALID_OPERATOR_SEQUENCE", 9, 22);
        assertContains(result, CheckCategory.CONTROL_HEADER, "ERR_MISSING_HEADER_DELIMITER", 12, 15);

        Set<Integer> errorLines = result.diagnostics().stream()
            .map(d -> d.location().line())
            .collect(Collectors.toSet());
        assertTrue(errorLines.contains(5));
        assertTrue(errorLines.contains(9));
        assertTrue(errorLines.contains(12));
        // Valid scaffolding lines carry no diagnostics: package (1), class (3),
        // method signatures (4, 8, 11).
        for (int cleanLine : List.of(1, 3, 4, 8, 11)) {
            assertFalse(errorLines.contains(cleanLine),
                "Cascading false positive on clean line " + cleanLine + ": " + result.diagnostics());
        }
    }

    @Test
    @DisplayName("Direct adapter execution: JavaAdapter parses valid fixture without errors")
    void directAdapterValidFixture() {
        String code = loadFixture("/fixtures/java/valid/CompleteProgram.java");
        JavaAdapter adapter = new JavaAdapter();
        ParseResult result = adapter.parse(SourcePayload.file(code, "CompleteProgram.java", Language.JAVA));
        assertTrue(result.isSuccessful(), () -> "Expected success but got: " + result.diagnostics());
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("Direct checker execution: DelimiterMatchingChecker flags InvalidDelimiters")
    void directCheckerInvalidDelimiters() {
        String code = loadFixture("/fixtures/java/invalid/InvalidDelimiters.java");
        SourcePayload payload = SourcePayload.file(code, "InvalidDelimiters.java", Language.JAVA);
        List<Token> tokens = Tokenizer.forLanguage(Language.JAVA).tokenize(payload);
        List<Diagnostic> diagnostics = new DelimiterMatchingChecker().check(payload, tokens);
        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals(CheckCategory.DELIMITER_MATCH, diag.category());
        assertEquals(DelimiterMatchingChecker.ERR_MISMATCHED_DELIMITER, diag.code());
        assertEquals(5, diag.location().line());
        assertEquals(23, diag.location().column());
        assertEquals(Severity.ERROR, diag.severity());
    }
}
