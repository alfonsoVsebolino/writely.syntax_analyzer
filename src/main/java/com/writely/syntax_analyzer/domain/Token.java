package com.writely.syntax_analyzer.domain;

import java.util.Objects;

/**
 * Represents a discrete lexical token produced by a language lexer.
 */
public record Token(
    TokenType tokenType,
    String lexeme,
    SourceSpan span,
    String category
) {
    public Token {
        Objects.requireNonNull(tokenType, "tokenType must not be null");
        Objects.requireNonNull(lexeme, "lexeme must not be null");
        Objects.requireNonNull(span, "span must not be null");
        Objects.requireNonNull(category, "category must not be null");
    }

    public static Token of(TokenType tokenType, String lexeme, SourceSpan span, String category) {
        return new Token(tokenType, lexeme, span, category);
    }

    public static Token of(TokenType tokenType, String lexeme, SourceSpan span) {
        return new Token(tokenType, lexeme, span, tokenType.name());
    }

    public SourceLocation startLocation() {
        return span.start();
    }

    public SourceLocation endLocation() {
        return span.end();
    }

    public boolean isTrivia() {
        return tokenType.isTrivia();
    }
}
