package com.writely.syntax_analyzer.core.report;

import com.writely.syntax_analyzer.core.analysis.DiagnosticAggregator;
import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.DiagnosticSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Formats an {@link AnalysisResult} to a deterministic JSON string
 * without external JSON library dependencies.
 */
public class JsonReportFormatter {

    /**
     * Formats the analysis result into pretty-printed deterministic JSON.
     *
     * @param result analysis result to format; must not be null
     * @return pretty-printed JSON string
     */
    public String format(AnalysisResult result) {
        return format(result, true);
    }

    /**
     * Formats the analysis result into deterministic JSON.
     *
     * @param result analysis result to format; must not be null
     * @param pretty whether to format with indentation and line breaks
     * @return JSON string
     */
    public String format(AnalysisResult result, boolean pretty) {
        Objects.requireNonNull(result, "result must not be null");
        DiagnosticSummary summary = result.summary();

        StringBuilder sb = new StringBuilder();
        String indent1 = pretty ? "  " : "";
        String indent2 = pretty ? "    " : "";
        String indent3 = pretty ? "      " : "";
        String nl = pretty ? "\n" : "";
        String space = pretty ? " " : "";

        sb.append("{").append(nl);

        // Root fields: language, inputMode, sourceName, status
        appendProperty(sb, indent1, "language", result.payload().language().name(), true, pretty);
        appendProperty(sb, indent1, "inputMode", result.payload().inputMode().name(), true, pretty);
        appendProperty(sb, indent1, "sourceName", result.payload().sourceName(), true, pretty);
        appendProperty(sb, indent1, "status", summary.status().name(), true, pretty);

        // summary object
        sb.append(indent1).append("\"summary\":").append(space).append("{").append(nl);
        appendIntProperty(sb, indent2, "totalLines", summary.totalLines(), true, pretty);
        appendIntProperty(sb, indent2, "totalTokens", summary.totalTokens(), true, pretty);
        appendIntProperty(sb, indent2, "errorCount", summary.errorCount(), true, pretty);
        appendIntProperty(sb, indent2, "warningCount", summary.warningCount(), true, pretty);
        appendIntProperty(sb, indent2, "flaggedLines", summary.flaggedLines(), true, pretty);
        appendIntProperty(sb, indent2, "validLines", summary.validLines(), true, pretty);

        // categoryCounts map
        sb.append(indent2).append("\"categoryCounts\":").append(space).append("{").append(nl);
        CheckCategory[] categories = CheckCategory.values();
        for (int i = 0; i < categories.length; i++) {
            CheckCategory cat = categories[i];
            int count = summary.categoryCounts().getOrDefault(cat, 0);
            boolean hasMore = i < categories.length - 1;
            appendIntProperty(sb, indent3, cat.name(), count, hasMore, pretty);
        }
        sb.append(indent2).append("}").append(nl);
        sb.append(indent1).append("},").append(nl);

        // diagnostics array
        List<Diagnostic> sortedDiagnostics = new ArrayList<>(result.diagnostics());
        sortedDiagnostics.sort(DiagnosticAggregator.order());

        sb.append(indent1).append("\"diagnostics\":").append(space);
        if (sortedDiagnostics.isEmpty()) {
            sb.append("[]").append(nl);
        } else {
            sb.append("[").append(nl);
            for (int i = 0; i < sortedDiagnostics.size(); i++) {
                Diagnostic d = sortedDiagnostics.get(i);
                boolean hasMore = i < sortedDiagnostics.size() - 1;
                sb.append(indent2).append("{").append(nl);
                appendProperty(sb, indent3, "code", d.code(), true, pretty);
                appendProperty(sb, indent3, "category", d.category().name(), true, pretty);
                appendProperty(sb, indent3, "severity", d.severity().name(), true, pretty);
                appendIntProperty(sb, indent3, "line", d.line(), true, pretty);
                appendIntProperty(sb, indent3, "column", d.column(), true, pretty);
                appendProperty(sb, indent3, "message", d.message(), false, pretty);
                sb.append(indent2).append("}").append(hasMore ? "," : "").append(nl);
            }
            sb.append(indent1).append("]").append(nl);
        }

        sb.append("}");
        if (pretty) {
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendProperty(StringBuilder sb, String indent, String key, String value, boolean trailingComma, boolean pretty) {
        String space = pretty ? " " : "";
        String nl = pretty ? "\n" : "";
        sb.append(indent).append("\"").append(escapeString(key)).append("\":").append(space)
          .append("\"").append(escapeString(value)).append("\"")
          .append(trailingComma ? "," : "")
          .append(nl);
    }

    private void appendIntProperty(StringBuilder sb, String indent, String key, int value, boolean trailingComma, boolean pretty) {
        String space = pretty ? " " : "";
        String nl = pretty ? "\n" : "";
        sb.append(indent).append("\"").append(escapeString(key)).append("\":").append(space)
          .append(value)
          .append(trailingComma ? "," : "")
          .append(nl);
    }

    /**
     * Escapes a string for safe inclusion inside a JSON string literal.
     * Escapes double quotes, backslashes, whitespace control characters,
     * and non-printable control characters below {@code 0x20}.
     *
     * @param input raw string to escape; null returns empty string
     * @return escaped JSON string
     */
    public static String escapeString(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
