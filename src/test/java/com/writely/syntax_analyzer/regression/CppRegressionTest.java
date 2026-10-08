package com.writely.syntax_analyzer.regression;

import com.writely.syntax_analyzer.adapter.CppAdapter;
import com.writely.syntax_analyzer.adapter.ParseResult;
import com.writely.syntax_analyzer.core.analysis.AnalysisOrchestrator;
import com.writely.syntax_analyzer.core.check.OperatorSyntaxChecker;
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
 * Cross-language regression suite for C++ (issue #12).
 */
@DisplayName("C++ Regression Tests (Issue #12)")
class CppRegressionTest {

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
            SourcePayload.singleLine(code, Language.CPP),
            SourcePayload.snippet(code, Language.CPP),
            SourcePayload.file(code, fileName, Language.CPP)
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
        String code = loadFixture("/fixtures/cpp/valid/CompleteProgram.cpp");
        assertValidAcrossModes(code, "CompleteProgram.cpp");
    }

    @Test
    @DisplayName("Valid Snippet passes in all three input modes")
    void validSnippetAllModes() {
        String code = loadFixture("/fixtures/cpp/valid/Snippet.cpp");
        assertValidAcrossModes(code, "Snippet.cpp");
    }

    @Test
    @DisplayName("InvalidDelimiters: mismatched (] at L2C19")
    void invalidDelimiters() {
        String code = loadFixture("/fixtures/cpp/invalid/InvalidDelimiters.cpp");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidDelimiters.cpp", Language.CPP));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.DELIMITER_MATCH, "ERR_MISMATCHED_DELIMITER", 2, 19);
    }

    @Test
    @DisplayName("InvalidLiterals: unclosed string at L2C21")
    void invalidLiterals() {
        String code = loadFixture("/fixtures/cpp/invalid/InvalidLiterals.cpp");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidLiterals.cpp", Language.CPP));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.LITERAL_SYNTAX, "ERR_UNCLOSED_STRING_LITERAL", 2, 21);
    }

    @Test
    @DisplayName("InvalidTerminatorsAndBlocks: missing semicolon at L2C15")
    void invalidTerminatorsAndBlocks() {
        String code = loadFixture("/fixtures/cpp/invalid/InvalidTerminatorsAndBlocks.cpp");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidTerminatorsAndBlocks.cpp", Language.CPP));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.STATEMENT_TERMINATOR, "ERR_MISSING_SEMICOLON", 2, 15);
    }

    @Test
    @DisplayName("InvalidOperators: missing operand at L2C15")
    void invalidOperators() {
        String code = loadFixture("/fixtures/cpp/invalid/InvalidOperators.cpp");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidOperators.cpp", Language.CPP));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.OPERATOR_SYNTAX, "ERR_MISSING_OPERAND", 2, 15);
    }

    @Test
    @DisplayName("InvalidControlHeaders: missing '(' at L3C11")
    void invalidControlHeaders() {
        String code = loadFixture("/fixtures/cpp/invalid/InvalidControlHeaders.cpp");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidControlHeaders.cpp", Language.CPP));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.CONTROL_HEADER, "ERR_MISSING_HEADER_DELIMITER", 3, 11);
    }

    @Test
    @DisplayName("InvalidIdentifiers: keyword-as-identifier at L2C9")
    void invalidIdentifiers() {
        String code = loadFixture("/fixtures/cpp/invalid/InvalidIdentifiers.cpp");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "InvalidIdentifiers.cpp", Language.CPP));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
        assertContains(result, CheckCategory.IDENTIFIER_NAMING, "ERR_KEYWORD_AS_IDENTIFIER", 2, 9);
    }

    @Test
    @DisplayName("MultiErrorRecovery: three independent errors, no cascading false positives")
    void multiErrorRecovery() {
        String code = loadFixture("/fixtures/cpp/invalid/MultiErrorRecovery.cpp");
        AnalysisResult result = orchestrator.analyze(SourcePayload.file(code, "MultiErrorRecovery.cpp", Language.CPP));
        assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());

        assertContains(result, CheckCategory.STATEMENT_TERMINATOR, "ERR_MISSING_SEMICOLON", 2, 15);
        assertContains(result, CheckCategory.OPERATOR_SYNTAX, "ERR_MISSING_OPERAND", 8, 15);
        assertContains(result, CheckCategory.CONTROL_HEADER, "ERR_MISSING_HEADER_DELIMITER", 13, 11);

        Set<Integer> errorLines = result.diagnostics().stream()
            .map(d -> d.location().line())
            .collect(Collectors.toSet());
        assertTrue(errorLines.contains(2));
        assertTrue(errorLines.contains(8));
        assertTrue(errorLines.contains(13));
        // Valid scaffolding lines carry no diagnostics: function signatures
        // (1, 7, 12) and clean bodies (3, 9).
        for (int cleanLine : List.of(1, 3, 7, 9, 12)) {
            assertFalse(errorLines.contains(cleanLine),
                "Cascading false positive on clean line " + cleanLine + ": " + result.diagnostics());
        }
    }

    @Test
    @DisplayName("Direct adapter execution: CppAdapter parses valid fixture without errors")
    void directAdapterValidFixture() {
        String code = loadFixture("/fixtures/cpp/valid/CompleteProgram.cpp");
        CppAdapter adapter = new CppAdapter();
        ParseResult result = adapter.parse(SourcePayload.file(code, "CompleteProgram.cpp", Language.CPP));
        assertTrue(result.isSuccessful(), () -> "Expected success but got: " + result.diagnostics());
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("Direct checker execution: OperatorSyntaxChecker flags InvalidOperators")
    void directCheckerInvalidOperators() {
        String code = loadFixture("/fixtures/cpp/invalid/InvalidOperators.cpp");
        SourcePayload payload = SourcePayload.file(code, "InvalidOperators.cpp", Language.CPP);
        List<Token> tokens = Tokenizer.forLanguage(Language.CPP).tokenize(payload);
        List<Diagnostic> diagnostics = new OperatorSyntaxChecker().check(payload, tokens);
        boolean found = diagnostics.stream().anyMatch(d ->
            d.category() == CheckCategory.OPERATOR_SYNTAX
                && d.code().equals(OperatorSyntaxChecker.ERR_MISSING_OPERAND)
                && d.location().line() == 2
                && d.location().column() == 15
                && d.severity() == Severity.ERROR);
        assertTrue(found, () -> "Expected OPERATOR_SYNTAX/ERR_MISSING_OPERAND at L2C15 but got: " + diagnostics);
    }
}
