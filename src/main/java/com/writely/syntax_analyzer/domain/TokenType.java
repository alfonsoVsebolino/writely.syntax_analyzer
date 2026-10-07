package com.writely.syntax_analyzer.domain;

/**
 * Universal token types across supported programming languages.
 */
public enum TokenType {
    KEYWORD,
    IDENTIFIER,
    LITERAL_STRING,
    LITERAL_CHAR,
    LITERAL_NUMBER,
    OPERATOR,
    DELIMITER,
    COMMENT,
    INDENT,
    DEDENT,
    NEWLINE,
    WHITESPACE,
    UNKNOWN;

    /**
     * Indicates whether this token represents any literal value (string, char, number).
     */
    public boolean isLiteral() {
        return this == LITERAL_STRING || this == LITERAL_CHAR || this == LITERAL_NUMBER;
    }

    /**
     * Indicates whether this token represents trivia (whitespace, comment, newline).
     */
    public boolean isTrivia() {
        return this == WHITESPACE || this == COMMENT || this == NEWLINE;
    }
}
