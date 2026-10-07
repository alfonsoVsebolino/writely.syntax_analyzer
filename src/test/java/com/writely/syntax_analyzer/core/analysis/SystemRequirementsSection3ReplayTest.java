package com.writely.syntax_analyzer.core.analysis;

import com.writely.syntax_analyzer.adapter.PythonAdapter;
import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.AnalysisStatus;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("system_requirements.md Section 3 Replay Tests")
class SystemRequirementsSection3ReplayTest {

    /**
     * Example 1 sample input (Input Mode 2 - Multi-Line Block), 3 lines.
     */
    private static final String EXAMPLE_1 = "x = 10\nif (x > 5):\n    print(\"Value is valid\")";

    /**
     * Example 2 sample input (Input Mode 3 - File Upload), 4 lines.
     */
    private static final String EXAMPLE_2 = "x = 10\nif (x > 5\n    print(\"Value is valid)\ny = 20 + * 5";

    /**
     * The three findings documented in the Example 2 expected output report,
     * in the report's own categories, locations, codes, and wording.
     */
    private static List<Diagnostic> example2DocumentedFindings() {
        return List.of(
            Diagnostic.error(
                CheckCategory.DELIMITER_MATCH,
                SourceLocation.of(2, 4),
                "Unclosed parenthesis '('. Expected ')' before line end.",
                "ERR_UNCLOSED_DELIMITER"),
            Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                SourceLocation.of(3, 11),
                "Unclosed string literal. Missing matching double quote '\"'.",
                "ERR_UNCLOSED_STRING_LITERAL"),
            Diagnostic.error(
                CheckCategory.OPERATOR_SYNTAX,
                SourceLocation.of(4, 10),
                "Consecutive binary operators '+ *' without an operand in between.",
                "ERR_INVALID_OPERATOR_SEQUENCE")
        );
    }

    private static void assertSorted(List<Diagnostic> diagnostics) {
        for (int i = 1; i < diagnostics.size(); i++) {
            Diagnostic previous = diagnostics.get(i - 1);
            Diagnostic current = diagnostics.get(i);
            assertTrue(DiagnosticAggregator.order().compare(previous, current) <= 0,
                () -> "findings out of order: " + previous + " before " + current);
        }
    }

    @Nested
    @DisplayName("Example 1: valid 3-line Python input")
    class Example1Tests {

        @Test
        @DisplayName("Valid input yields 0 diagnostics, PASSED, and validLines == totalLines")
        void validInputPasses() {
            SourcePayload payload = SourcePayload.snippet(EXAMPLE_1, Language.PYTHON);
            assertEquals(3, payload.lineCount());

            AnalysisResult result = new AnalysisOrchestrator().analyze(payload);

            assertTrue(result.diagnostics().isEmpty(),
                "expected no findings, but got: " + result.diagnostics());
            assertEquals(0, result.summary().errorCount());
            assertEquals(0, result.summary().warningCount());
            assertEquals(0, result.summary().flaggedLines());
            assertEquals(3, result.summary().totalLines());
            assertEquals(result.summary().totalLines(), result.summary().validLines());
            assertEquals(AnalysisStatus.PASSED, result.summary().status());
            assertTrue(result.isPassed());
        }
    }

    @Nested
    @DisplayName("Example 2: 4-line Python input with 3 documented errors")
    class Example2Tests {

        @Test
        @DisplayName("Documented findings aggregate to exactly 3 sorted diagnostics with the specified summary")
        void documentedFindingsAggregateToThree() {
            List<Diagnostic> findings = example2DocumentedFindings();
            SourcePayload payload = SourcePayload.file(EXAMPLE_2, "example2.py", Language.PYTHON);
            List<Token> tokens = new PythonAdapter().tokenize(payload);

            List<Diagnostic> aggregated = DiagnosticAggregator.aggregate(findings);

            assertEquals(3, aggregated.size());
            assertEquals(findings, aggregated, "expected report order: line 2, line 3, line 4");
            assertSorted(aggregated);

            AnalysisResult result = AnalysisResult.of(payload, tokens, Optional.empty(), aggregated);

            assertEquals(3, result.summary().errorCount());
            assertEquals(0, result.summary().warningCount());
            assertEquals(3, result.diagnostics().size());
            assertEquals(4, result.summary().totalLines());
            assertEquals(3, result.summary().flaggedLines());
            assertEquals(1, result.summary().validLines());
            assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
            assertTrue(result.hasErrors());
        }

        @Test
        @DisplayName("Documented findings aggregate identically regardless of input order")
        void documentedFindingsAggregateRegardlessOfInputOrder() {
            List<Diagnostic> findings = new ArrayList<>(example2DocumentedFindings());
            List<Diagnostic> shuffled = new ArrayList<>(findings);
            Collections.reverse(shuffled);

            assertEquals(DiagnosticAggregator.aggregate(findings), DiagnosticAggregator.aggregate(shuffled));
            assertEquals(3, DiagnosticAggregator.aggregate(shuffled).size());
        }

        @Test
        @DisplayName("Full pipeline on the Example 2 payload reports the three documented findings on flagged lines 2-4")
        void fullPipelineReportsDocumentedFindings() {
            SourcePayload payload = SourcePayload.file(EXAMPLE_2, "example2.py", Language.PYTHON);
            assertEquals(4, payload.lineCount());

            AnalysisResult result = new AnalysisOrchestrator().analyze(payload);
            List<Diagnostic> diagnostics = result.diagnostics();

            assertFalse(diagnostics.isEmpty());
            assertSorted(diagnostics);

            // the three documented errors are found
            assertTrue(diagnostics.stream().anyMatch(d ->
                    d.category() == CheckCategory.DELIMITER_MATCH && d.location().line() == 2),
                "missing documented delimiter finding on line 2: " + diagnostics);
            assertTrue(diagnostics.stream().anyMatch(d ->
                    d.category() == CheckCategory.LITERAL_SYNTAX && d.location().line() == 3),
                "missing documented string literal finding on line 3: " + diagnostics);
            assertTrue(diagnostics.stream().anyMatch(d ->
                    d.category() == CheckCategory.OPERATOR_SYNTAX && d.location().line() == 4),
                "missing documented operator finding on line 4: " + diagnostics);

            // every finding sits on one of the three flagged lines
            for (Diagnostic d : diagnostics) {
                assertTrue(d.location().line() >= 2 && d.location().line() <= 4,
                    "unexpected finding outside flagged lines 2-4: " + d);
                assertEquals(Severity.ERROR, d.severity());
            }

            assertEquals(4, result.summary().totalLines());
            assertEquals(3, result.summary().flaggedLines());
            assertEquals(1, result.summary().validLines());
            assertEquals(AnalysisStatus.FAILED_SYNTAX_ERRORS, result.summary().status());
            assertEquals(diagnostics.size(), result.summary().errorCount());
        }
    }
}
