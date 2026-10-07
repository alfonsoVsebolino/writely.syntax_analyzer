package com.writely.syntax_analyzer.domain;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Summary metrics and outcome status aggregated from an analysis run.
 *
 * <p>{@code flaggedLines} is the number of distinct source lines carrying at least one
 * diagnostic (lines outside the payload range {@code [1, totalLines]} are not counted);
 * {@code validLines} is always {@code totalLines - flaggedLines}.</p>
 */
public record DiagnosticSummary(
    int totalLines,
    int totalTokens,
    int errorCount,
    int warningCount,
    int flaggedLines,
    int validLines,
    Map<CheckCategory, Integer> categoryCounts,
    AnalysisStatus status
) {
    public DiagnosticSummary {
        if (totalLines < 0) {
            throw new IllegalArgumentException("totalLines must be >= 0, but was: " + totalLines);
        }
        if (totalTokens < 0) {
            throw new IllegalArgumentException("totalTokens must be >= 0, but was: " + totalTokens);
        }
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must be >= 0, but was: " + errorCount);
        }
        if (warningCount < 0) {
            throw new IllegalArgumentException("warningCount must be >= 0, but was: " + warningCount);
        }
        if (flaggedLines < 0) {
            throw new IllegalArgumentException("flaggedLines must be >= 0, but was: " + flaggedLines);
        }
        if (validLines < 0) {
            throw new IllegalArgumentException("validLines must be >= 0, but was: " + validLines);
        }
        if (flaggedLines + validLines != totalLines) {
            throw new IllegalArgumentException(
                "flaggedLines + validLines must equal totalLines, but was: "
                    + flaggedLines + " + " + validLines + " != " + totalLines);
        }
        Objects.requireNonNull(categoryCounts, "categoryCounts must not be null");
        Objects.requireNonNull(status, "status must not be null");
        categoryCounts = Map.copyOf(categoryCounts);
    }

    /**
     * Calculates summary metrics from raw totals and emitted diagnostics.
     */
    public static DiagnosticSummary calculate(int totalLines, int totalTokens, List<Diagnostic> diagnostics) {
        if (totalLines < 0) {
            throw new IllegalArgumentException("totalLines must be >= 0, but was: " + totalLines);
        }
        if (totalTokens < 0) {
            throw new IllegalArgumentException("totalTokens must be >= 0, but was: " + totalTokens);
        }
        List<Diagnostic> safeDiagnostics = diagnostics == null ? List.of() : diagnostics;

        int errorCount = 0;
        int warningCount = 0;
        Set<Integer> flaggedLineNumbers = new HashSet<>();
        Map<CheckCategory, Integer> counts = new EnumMap<>(CheckCategory.class);
        for (CheckCategory cat : CheckCategory.values()) {
            counts.put(cat, 0);
        }

        for (Diagnostic d : safeDiagnostics) {
            if (d == null) {
                continue;
            }
            if (d.severity() == Severity.ERROR) {
                errorCount++;
            } else if (d.severity() == Severity.WARNING) {
                warningCount++;
            }
            counts.put(d.category(), counts.get(d.category()) + 1);
            int line = d.location().line();
            if (line >= 1 && line <= totalLines) {
                flaggedLineNumbers.add(line);
            }
        }

        int flaggedLines = flaggedLineNumbers.size();
        int validLines = totalLines - flaggedLines;

        AnalysisStatus status = errorCount > 0 ? AnalysisStatus.FAILED_SYNTAX_ERRORS : AnalysisStatus.PASSED;

        return new DiagnosticSummary(
            totalLines,
            totalTokens,
            errorCount,
            warningCount,
            flaggedLines,
            validLines,
            counts,
            status
        );
    }

    /**
     * Calculates summary metrics using source payload and token list.
     */
    public static DiagnosticSummary calculate(SourcePayload payload, List<Token> tokens, List<Diagnostic> diagnostics) {
        Objects.requireNonNull(payload, "payload must not be null");
        int totalLines = payload.lineCount();
        int totalTokens = tokens == null ? 0 : tokens.size();
        return calculate(totalLines, totalTokens, diagnostics);
    }

    /**
     * Returns diagnostic count for the specified check category.
     */
    public int countForCategory(CheckCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        return categoryCounts.getOrDefault(category, 0);
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    public boolean isPassed() {
        return status == AnalysisStatus.PASSED;
    }
}
