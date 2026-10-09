package com.writely.syntax_analyzer.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Universal aggregate result model containing the ingested payload, emitted token stream,
 * optional syntax tree, diagnostic findings, and statistical summary.
 */
public record AnalysisResult(
    SourcePayload payload,
    List<Token> tokens,
    Optional<SyntaxNode> syntaxTree,
    List<Diagnostic> diagnostics,
    DiagnosticSummary summary
) {
    public AnalysisResult {
        Objects.requireNonNull(payload, "payload must not be null");
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        Objects.requireNonNull(syntaxTree, "syntaxTree must not be null");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        Objects.requireNonNull(summary, "summary must not be null");
    }

    public static AnalysisResult of(
        SourcePayload payload,
        List<Token> tokens,
        Optional<SyntaxNode> syntaxTree,
        List<Diagnostic> diagnostics
    ) {
        DiagnosticSummary summary = DiagnosticSummary.calculate(payload, tokens, diagnostics);
        return new AnalysisResult(payload, tokens, syntaxTree, diagnostics, summary);
    }

    public static AnalysisResult of(
        SourcePayload payload,
        List<Token> tokens,
        SyntaxNode syntaxTree,
        List<Diagnostic> diagnostics
    ) {
        return of(payload, tokens, Optional.ofNullable(syntaxTree), diagnostics);
    }

    public static AnalysisResult of(
        SourcePayload payload,
        List<Token> tokens,
        List<Diagnostic> diagnostics
    ) {
        return of(payload, tokens, Optional.empty(), diagnostics);
    }

    public boolean isPassed() {
        return summary.isPassed();
    }

    public boolean hasErrors() {
        return summary.hasErrors();
    }

    public int errorCount() {
        return summary.errorCount();
    }

    public int warningCount() {
        return summary.warningCount();
    }

    /**
     * Alias for syntaxTree() representing the root AST node if parsing succeeded.
     */
    public Optional<SyntaxNode> rootNode() {
        return syntaxTree;
    }
}
