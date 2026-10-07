package com.writely.syntax_analyzer.core.analysis;

import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Diagnostic Aggregation Tests")
class DiagnosticAggregatorTest {

    private static Diagnostic diagnostic(
        CheckCategory category,
        int line,
        int column,
        String code,
        String message
    ) {
        return Diagnostic.of(category, Severity.ERROR, SourceLocation.of(line, column), message, code);
    }

    private static void assertSorted(List<Diagnostic> diagnostics) {
        for (int i = 1; i < diagnostics.size(); i++) {
            Diagnostic previous = diagnostics.get(i - 1);
            Diagnostic current = diagnostics.get(i);
            int byLine = Integer.compare(previous.location().line(), current.location().line());
            int byColumn = Integer.compare(previous.location().column(), current.location().column());
            int byCategory = Integer.compare(previous.category().ordinal(), current.category().ordinal());
            int byCode = previous.code().compareTo(current.code());
            int cmp = byLine != 0 ? byLine : byColumn != 0 ? byColumn : byCategory != 0 ? byCategory : byCode;
            assertTrue(cmp <= 0, () -> "out of order: " + previous + " before " + current);
        }
    }

    @Nested
    @DisplayName("Exact duplicate de-duplication")
    class DeduplicationTests {

        @Test
        @DisplayName("Exact duplicates on (category, code, line, column, message) collapse to one")
        void exactDuplicatesCollapse() {
            Diagnostic first = diagnostic(CheckCategory.DELIMITER_MATCH, 2, 4, "ERR_A", "unclosed '('");
            Diagnostic duplicate = diagnostic(CheckCategory.DELIMITER_MATCH, 2, 4, "ERR_A", "unclosed '('");
            Diagnostic other = diagnostic(CheckCategory.LITERAL_SYNTAX, 3, 5, "ERR_B", "unclosed '\"'");

            List<Diagnostic> aggregated = DiagnosticAggregator.aggregate(List.of(first, duplicate, other, duplicate));

            assertEquals(2, aggregated.size());
            assertTrue(aggregated.contains(first));
            assertTrue(aggregated.contains(other));
        }

        @Test
        @DisplayName("Feeding the identical list twice produces identical output")
        void identicalListTwiceProducesIdenticalOutput() {
            List<Diagnostic> input = new ArrayList<>(List.of(
                diagnostic(CheckCategory.OPERATOR_SYNTAX, 4, 10, "ERR_OP", "bad sequence"),
                diagnostic(CheckCategory.OPERATOR_SYNTAX, 4, 10, "ERR_OP", "bad sequence"),
                diagnostic(CheckCategory.DELIMITER_MATCH, 2, 4, "ERR_DELIM", "unclosed"),
                diagnostic(CheckCategory.CONTROL_HEADER, 1, 1, "ERR_HDR", "missing colon")
            ));

            List<Diagnostic> firstRun = DiagnosticAggregator.aggregate(input);
            List<Diagnostic> secondRun = DiagnosticAggregator.aggregate(input);

            assertEquals(firstRun, secondRun);

            List<Diagnostic> doubled = new ArrayList<>(input);
            doubled.addAll(input);
            assertEquals(firstRun, DiagnosticAggregator.aggregate(doubled));
        }

        @Test
        @DisplayName("Aggregation is idempotent: aggregate(aggregate(x)) == aggregate(x)")
        void aggregationIsIdempotent() {
            List<Diagnostic> input = new ArrayList<>(List.of(
                diagnostic(CheckCategory.IDENTIFIER_NAMING, 7, 2, "ERR_ID", "bad name"),
                diagnostic(CheckCategory.IDENTIFIER_NAMING, 7, 2, "ERR_ID", "bad name"),
                diagnostic(CheckCategory.STATEMENT_TERMINATOR, 5, 9, "ERR_ST", "missing ';'")
            ));

            List<Diagnostic> once = DiagnosticAggregator.aggregate(input);
            List<Diagnostic> twice = DiagnosticAggregator.aggregate(once);

            assertEquals(once, twice);
            assertEquals(2, twice.size());
        }

        @Test
        @DisplayName("Different categories at the same location both survive")
        void differentCategoriesAtSameLocationSurvive() {
            Diagnostic delimiter = diagnostic(CheckCategory.DELIMITER_MATCH, 3, 11, "SAME_CODE", "same message");
            Diagnostic literal = diagnostic(CheckCategory.LITERAL_SYNTAX, 3, 11, "SAME_CODE", "same message");
            Diagnostic operator = diagnostic(CheckCategory.OPERATOR_SYNTAX, 3, 11, "SAME_CODE", "same message");

            List<Diagnostic> aggregated =
                DiagnosticAggregator.aggregate(List.of(delimiter, literal, operator));

            assertEquals(3, aggregated.size());
            assertSorted(aggregated);
        }

        @Test
        @DisplayName("Different codes or messages at the same location survive")
        void differentCodesOrMessagesAtSameLocationSurvive() {
            Diagnostic codeA = diagnostic(CheckCategory.DELIMITER_MATCH, 4, 10, "ERR_A", "same message");
            Diagnostic codeB = diagnostic(CheckCategory.DELIMITER_MATCH, 4, 10, "ERR_B", "same message");
            Diagnostic messageB = diagnostic(CheckCategory.DELIMITER_MATCH, 4, 10, "ERR_A", "other message");

            List<Diagnostic> aggregated =
                DiagnosticAggregator.aggregate(List.of(codeA, codeB, messageB));

            assertEquals(3, aggregated.size());
        }

        @Test
        @DisplayName("Null inputs and null entries are handled safely")
        void nullInputsAreHandledSafely() {
            assertTrue(DiagnosticAggregator.aggregate(null).isEmpty());
            assertTrue(DiagnosticAggregator.aggregate(List.of()).isEmpty());

            List<Diagnostic> withNull = new ArrayList<>();
            withNull.add(null);
            withNull.add(diagnostic(CheckCategory.CONTROL_HEADER, 1, 2, "ERR_HDR", "missing colon"));
            withNull.add(null);

            List<Diagnostic> aggregated = DiagnosticAggregator.aggregate(withNull);
            assertEquals(1, aggregated.size());
            assertEquals("ERR_HDR", aggregated.get(0).code());
        }
    }

    @Nested
    @DisplayName("Ordering contract")
    class OrderingTests {

        @Test
        @DisplayName("Findings sort by (line asc, column asc, category ordinal, code asc)")
        void sortOrderMatchesContract() {
            Diagnostic line3Column1 =
                diagnostic(CheckCategory.DELIMITER_MATCH, 3, 1, "ERR_A", "third line");
            Diagnostic line1Column9 =
                diagnostic(CheckCategory.DELIMITER_MATCH, 1, 9, "ERR_A", "late column");
            Diagnostic line1Column2Operator =
                diagnostic(CheckCategory.OPERATOR_SYNTAX, 1, 2, "ERR_Z", "operator");
            Diagnostic line1Column2Delimiter =
                diagnostic(CheckCategory.DELIMITER_MATCH, 1, 2, "ERR_Z", "delimiter");
            Diagnostic line1Column2DelimiterOtherCode =
                diagnostic(CheckCategory.DELIMITER_MATCH, 1, 2, "ERR_A", "delimiter other code");

            List<Diagnostic> input = List.of(
                line3Column1,
                line1Column9,
                line1Column2Operator,
                line1Column2Delimiter,
                line1Column2DelimiterOtherCode
            );

            List<Diagnostic> aggregated = DiagnosticAggregator.aggregate(input);

            assertEquals(
                List.of(
                    // line 1, column 2: DELIMITER_MATCH (ordinal 0) before OPERATOR_SYNTAX (ordinal 3),
                    // within the same category code ascending
                    line1Column2DelimiterOtherCode,
                    line1Column2Delimiter,
                    line1Column2Operator,
                    line1Column9,
                    line3Column1
                ),
                aggregated
            );
            assertSorted(aggregated);
        }

        @Test
        @DisplayName("Input order does not influence output order")
        void inputOrderDoesNotInfluenceOutputOrder() {
            List<Diagnostic> findings = new ArrayList<>(List.of(
                diagnostic(CheckCategory.CONTROL_HEADER, 5, 3, "ERR_C", "control"),
                diagnostic(CheckCategory.LITERAL_SYNTAX, 2, 8, "ERR_B", "literal"),
                diagnostic(CheckCategory.DELIMITER_MATCH, 2, 1, "ERR_A", "delimiter"),
                diagnostic(CheckCategory.IDENTIFIER_NAMING, 2, 1, "ERR_A", "identifier")
            ));

            List<Diagnostic> ascending = DiagnosticAggregator.aggregate(findings);
            List<Diagnostic> reversedInput = new ArrayList<>(findings);
            Collections.reverse(reversedInput);
            List<Diagnostic> reversed = DiagnosticAggregator.aggregate(reversedInput);

            assertEquals(ascending, reversed);
            assertSorted(reversed);
        }

        @Test
        @DisplayName("Aggregated result is immutable")
        void aggregatedResultIsImmutable() {
            List<Diagnostic> aggregated = DiagnosticAggregator.aggregate(List.of(
                diagnostic(CheckCategory.DELIMITER_MATCH, 1, 1, "ERR_A", "one")
            ));

            assertThrows(UnsupportedOperationException.class,
                () -> aggregated.add(diagnostic(CheckCategory.DELIMITER_MATCH, 2, 1, "ERR_B", "two")));
        }
    }
}
