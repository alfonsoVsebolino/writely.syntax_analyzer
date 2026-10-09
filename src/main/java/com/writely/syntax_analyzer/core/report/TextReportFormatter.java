package com.writely.syntax_analyzer.core.report;

import com.writely.syntax_analyzer.core.analysis.DiagnosticAggregator;
import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.Diagnostic;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Formats an {@link AnalysisResult} to a human-readable plain text report
 * matching system_requirements.md Section 3.
 */
public class TextReportFormatter {

    /**
     * Formats the given analysis result into a plain-text report.
     *
     * @param result analysis result to format; must not be null
     * @return formatted plain text report
     */
    public String format(AnalysisResult result) {
        Objects.requireNonNull(result, "result must not be null");

        StringBuilder sb = new StringBuilder();

        // 1. Header
        sb.append("SYNTACTICAL ANALYSIS REPORT\n");

        // 2. Status
        if (result.hasErrors()) {
            sb.append("Status: FAILED (")
              .append(result.errorCount())
              .append(" Syntax Errors Detected)\n");
        } else {
            sb.append("Status: PASSED (0 Syntax Errors Found)\n");
        }

        // 3. Metadata
        sb.append("Total Lines Analyzed: ")
          .append(result.summary().totalLines())
          .append("\n");

        String sourceName = result.payload().sourceName();
        if (isSourceNamePresent(sourceName)) {
            sb.append("Source: ").append(sourceName).append("\n");
        }

        // 4. Syntax Error Details (if errors exist)
        List<Diagnostic> errors = result.diagnostics().stream()
            .filter(Diagnostic::isError)
            .sorted(DiagnosticAggregator.order())
            .toList();

        String sourceText = result.payload().sourceText();
        String[] lines = (sourceText == null || sourceText.isEmpty())
            ? new String[0]
            : sourceText.split("\\r?\\n|\\r", -1);

        if (!errors.isEmpty()) {
            sb.append("\nSYNTAX ERROR DETAILS:\n");
            for (int i = 0; i < errors.size(); i++) {
                Diagnostic error = errors.get(i);
                int line = error.line();
                String lineContent = "";
                if (line >= 1 && line <= lines.length) {
                    lineContent = lines[line - 1];
                }

                sb.append("[ERROR ").append(i + 1).append("] Line ").append(line).append(":");
                if (!lineContent.isEmpty()) {
                    sb.append(" ").append(lineContent);
                }
                sb.append("\n");

                sb.append("- Category: ").append(error.category().displayName()).append("\n");
                sb.append("- Details: ").append(error.message()).append("\n");

                if (i < errors.size() - 1) {
                    sb.append("\n");
                }
            }
        }

        // 5. Summary section
        sb.append("\nSUMMARY:\n");
        sb.append("- Total Lines Checked: ").append(result.summary().totalLines()).append("\n");
        sb.append("- Valid Lines: ").append(result.summary().validLines()).append("\n");

        int flaggedCount = result.summary().flaggedLines();
        if (flaggedCount > 0) {
            Set<Integer> flaggedLineNumbers = new TreeSet<>();
            for (Diagnostic d : result.diagnostics()) {
                if (d != null) {
                    int line = d.line();
                    if (line >= 1 && (result.summary().totalLines() == 0 || line <= result.summary().totalLines())) {
                        flaggedLineNumbers.add(line);
                    }
                }
            }
            String lineList = flaggedLineNumbers.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
            sb.append("- Flagged Lines: ").append(flaggedCount).append(" (Lines ").append(lineList).append(")\n");
        } else {
            sb.append("- Flagged Lines: 0\n");
        }

        if (result.hasErrors()) {
            sb.append("- Action Required: Correct highlighted syntax errors above.\n");
        } else {
            sb.append("- Action Required: None. All syntax rules passed.\n");
        }

        return sb.toString();
    }

    private boolean isSourceNamePresent(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return false;
        }
        String trimmed = sourceName.trim();
        return !(trimmed.startsWith("<") && trimmed.endsWith(">"));
    }
}
