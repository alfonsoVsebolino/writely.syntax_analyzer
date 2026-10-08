package com.writely.syntax_analyzer.regression;

import com.writely.syntax_analyzer.adapter.PythonAdapter;
import com.writely.syntax_analyzer.adapter.ParseResult;
import com.writely.syntax_analyzer.core.analysis.AnalysisOrchestrator;
import com.writely.syntax_analyzer.core.check.StringAndCharacterLiteralChecker;
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
 * Cross-language regression suite for Python (issue #12).
 */
@DisplayName("Python Regression Tests (Issue #12)")
class PythonRegressionTest {

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
            SourcePayload.singleLine(code, Language.PYTHON),
            SourcePayload.snippet(code, Language.PYTHON),
            SourcePayload.file(code, fileName, Language.PYTHON)
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
        String code = loadFixture("/fixtures/python/valid/CompleteProgram.py");
        assertValidAcrossModes(code, "CompleteProgram.py");
    }

    @Test
    @DisplayName("Valid Snippet passes in all three input modes")
    void validSnippetAllModes() {
        String code = loadFixture("/fixtures/python/valid/Snippet.py");
        assertValidAcrossModes(code, "Snippet.py");
    }

    @Test
    @DisplayName("InvalidDelimiters: mismatched (] at L2C15")
    void invalidDelimiters() {
        String code = loadFixture("/fixtures/python/invalid/InvalidDelimiters.py");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidDelimiters.py", Language.PYTHON));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.DELIMITER_MATCH, "ERR_MISMATCHED_DELIMITER", 2, 15);
    }

    @Test
    @DisplayName("InvalidLiterals: unclosed string at L2C9")
    void invalidLiterals() {
        String code = loadFixture("/fixtures/python/invalid/InvalidLiterals.py");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidLiterals.py", Language.PYTHON));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.LITERAL_SYNTAX, "ERR_UNCLOSED_STRING_LITERAL", 2, 9);
    }

    @Test
    @DisplayName("InvalidTerminatorsAndBlocks: missing colon at L2C13")
    void invalidTerminatorsAndBlocks() {
        String code = loadFixture("/fixtures/python/invalid/InvalidTerminatorsAndBlocks.py");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidTerminatorsAndBlocks.py", Language.PYTHON));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.STATEMENT_TERMINATOR, "ERR_MISSING_COLON", 2, 13);
    }

    @Test
    @DisplayName("InvalidOperators: consecutive '+ *' at L2C14")
    void invalidOperators() {
        String code = loadFixture("/fixtures/python/invalid/InvalidOperators.py");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidOperators.py", Language.PYTHON));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.OPERATOR_SYNTAX, "ERR_INVALID_OPERATOR_SEQUENCE", 2, 14);
    }

    @Test
    @DisplayName("InvalidControlHeaders: malformed for-header at L2C9")
    void invalidControlHeaders() {
        String code = loadFixture("/fixtures/python/invalid/InvalidControlHeaders.py");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidControlHeaders.py", Language.PYTHON));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.CONTROL_HEADER, "ERR_MALFORMED_FOR_HEADER", 2, 9);
    }

    @Test
    @DisplayName("InvalidIdentifiers: leading digit at L2C1")
    void invalidIdentifiers() {
        String code = loadFixture("/fixtures/python/invalid/InvalidIdentifiers.py");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidIdentifiers.py", Language.PYTHON));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.IDENTIFIER_NAMING, "ERR_IDENTIFIER_STARTS_WITH_DIGIT", 2, 1);
    }

    @Test
    @DisplayName("MultiErrorRecovery: three independent errors, no cascading false positives")
    void multiErrorRecovery() {
        String code = loadFixture("/fixtures/python/invalid/MultiErrorRecovery.py");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "MultiErrorRecovery.py", Language.PYTHON));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());

        assertContains(result, CheckCategory.OPERATOR_SYNTAX, "ERR_INVALID_OPERATOR_SEQUENCE", 2, 14);
        assertContains(result, CheckCategory.DELIMITER_MATCH, "ERR_MISMATCHED_DELIMITER", 7, 15);
        assertContains(result, CheckCategory.LITERAL_SYNTAX, "ERR_UNCLOSED_STRING_LITERAL", 12, 9);

        Set<Integer> errorLines = result.diagnostics().stream()
            .map(d -> d.location().line())
            .collect(Collectors.toSet());
        assertTrue(errorLines.contains(2));
        assertTrue(errorLines.contains(7));
        assertTrue(errorLines.contains(12));
        // Valid scaffolding lines carry no diagnostics: def lines (1, 6, 11)
        // and clean returns (3, 8).
        for (int cleanLine : List.of(1, 3, 6, 8, 11)) {
            assertFalse(errorLines.contains(cleanLine),
                "Cascading false positive on clean line " + cleanLine + ": " + result.diagnostics());
        }
    }

    @Test
    @DisplayName("Direct adapter execution: PythonAdapter parses valid fixture without errors")
    void directAdapterValidFixture() {
        String code = loadFixture("/fixtures/python/valid/CompleteProgram.py");
        PythonAdapter adapter = new PythonAdapter();
        ParseResult result = adapter.parse(SourcePayload.file(code, "CompleteProgram.py", Language.PYTHON));
        assertTrue(result.isSuccessful(), () -> "Expected success but got: " + result.diagnostics());
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("Direct checker execution: literal checker flags InvalidLiterals")
    void directCheckerInvalidLiterals() {
        String code = loadFixture("/fixtures/python/invalid/InvalidLiterals.py");
        SourcePayload payload = SourcePayload.file(code, "InvalidLiterals.py", Language.PYTHON);
        List<Token> tokens = Tokenizer.forLanguage(Language.PYTHON).tokenize(payload);
        List<Diagnostic> diagnostics = new StringAndCharacterLiteralChecker().check(payload, tokens);
        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals(CheckCategory.LITERAL_SYNTAX, diag.category());
        assertEquals(StringAndCharacterLiteralChecker.ERR_UNCLOSED_STRING_LITERAL, diag.code());
        assertEquals(2, diag.location().line());
        assertEquals(9, diag.location().column());
        assertEquals(Severity.ERROR, diag.severity());
    }
}
