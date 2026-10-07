package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SyntaxNode;
import com.writely.syntax_analyzer.domain.Token;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Common parse result returned by parser adapters to the core pipeline,
 * encapsulating the syntax tree, diagnostics, scanned tokens, and optional native syntax info.
 */
public record ParseResult(
    Optional<SyntaxNode> syntaxTree,
    List<Diagnostic> diagnostics,
    Optional<Object> nativeSyntaxTree,
    List<Token> tokens
) {
    public ParseResult {
        Objects.requireNonNull(syntaxTree, "syntaxTree must not be null");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        Objects.requireNonNull(nativeSyntaxTree, "nativeSyntaxTree must not be null");
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
    }

    public static ParseResult of(
        SyntaxNode syntaxTree,
        List<Diagnostic> diagnostics,
        Object nativeSyntaxTree,
        List<Token> tokens
    ) {
        return new ParseResult(
            Optional.ofNullable(syntaxTree),
            diagnostics,
            Optional.ofNullable(nativeSyntaxTree),
            tokens
        );
    }

    public static ParseResult of(SyntaxNode syntaxTree, List<Diagnostic> diagnostics, Object nativeSyntaxTree) {
        return of(syntaxTree, diagnostics, nativeSyntaxTree, List.of());
    }

    public static ParseResult of(SyntaxNode syntaxTree, List<Diagnostic> diagnostics) {
        return of(syntaxTree, diagnostics, null, List.of());
    }

    public static ParseResult of(SyntaxNode syntaxTree) {
        return of(syntaxTree, List.of(), null, List.of());
    }

    public static ParseResult ofDiagnostics(List<Diagnostic> diagnostics) {
        return new ParseResult(Optional.empty(), diagnostics, Optional.empty(), List.of());
    }

    public static ParseResult ofTokens(List<Token> tokens) {
        return new ParseResult(Optional.empty(), List.of(), Optional.empty(), tokens);
    }

    public static ParseResult empty() {
        return new ParseResult(Optional.empty(), List.of(), Optional.empty(), List.of());
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }

    public boolean isSuccessful() {
        return !hasErrors();
    }

    public boolean hasSyntaxTree() {
        return syntaxTree.isPresent();
    }

    public boolean hasNativeSyntaxTree() {
        return nativeSyntaxTree.isPresent();
    }

    public <T> Optional<T> nativeSyntaxTree(Class<T> expectedType) {
        Objects.requireNonNull(expectedType, "expectedType must not be null");
        return nativeSyntaxTree.filter(expectedType::isInstance).map(expectedType::cast);
    }

    public int errorCount() {
        return (int) diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).count();
    }

    public int warningCount() {
        return (int) diagnostics.stream().filter(d -> d.severity() == Severity.WARNING).count();
    }
}
