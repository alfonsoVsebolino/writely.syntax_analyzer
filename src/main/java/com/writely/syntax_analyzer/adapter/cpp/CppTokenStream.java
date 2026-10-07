package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Token stream navigation for the C++ parser, providing k-token lookahead,
 * trivia filtering, and backtrack markers.
 */
public class CppTokenStream {

    private final List<Token> tokens;
    private final Token eofToken;
    private int cursor;
    private Token lastConsumed;

    public CppTokenStream(List<Token> rawTokens) {
        Objects.requireNonNull(rawTokens, "rawTokens must not be null");
        this.tokens = filterTokens(rawTokens);
        this.cursor = 0;
        SourceLocation endLoc = rawTokens.isEmpty()
            ? SourceLocation.start()
            : rawTokens.get(rawTokens.size() - 1).endLocation();
        this.eofToken = Token.of(TokenType.UNKNOWN, "<EOF>", SourceSpan.point(endLoc));
        this.lastConsumed = null;
    }

    private static List<Token> filterTokens(List<Token> raw) {
        List<Token> filtered = new ArrayList<>(raw.size());
        for (Token token : raw) {
            // Retain preprocessor directives; skip comments and trivia
            if (token.tokenType() == TokenType.COMMENT && !"PREPROCESSOR".equals(token.category())) {
                continue;
            }
            if (token.tokenType() == TokenType.WHITESPACE || token.tokenType() == TokenType.NEWLINE) {
                continue;
            }
            filtered.add(token);
        }
        return List.copyOf(filtered);
    }

    public boolean isAtEnd() {
        return cursor >= tokens.size();
    }

    public Token peek() {
        return peek(0);
    }

    public Token peek(int offset) {
        int target = cursor + offset;
        if (target >= 0 && target < tokens.size()) {
            return tokens.get(target);
        }
        return eofToken;
    }

    public Token consume() {
        if (!isAtEnd()) {
            lastConsumed = tokens.get(cursor);
            cursor++;
            return lastConsumed;
        }
        return eofToken;
    }

    public boolean check(String lexeme) {
        return check(0, lexeme);
    }

    public boolean check(int offset, String lexeme) {
        return !isAtEnd(offset) && peek(offset).lexeme().equals(lexeme);
    }

    public boolean check(TokenType type) {
        return check(0, type);
    }

    public boolean check(int offset, TokenType type) {
        return !isAtEnd(offset) && peek(offset).tokenType() == type;
    }

    public boolean isAtEnd(int offset) {
        return cursor + offset >= tokens.size();
    }

    public boolean match(String lexeme) {
        if (check(lexeme)) {
            consume();
            return true;
        }
        return false;
    }

    public boolean match(TokenType type) {
        if (check(type)) {
            consume();
            return true;
        }
        return false;
    }

    public boolean match(TokenType type, String lexeme) {
        if (check(type) && check(lexeme)) {
            consume();
            return true;
        }
        return false;
    }

    public Token lastConsumed() {
        return lastConsumed;
    }

    public SourceLocation location() {
        return peek().startLocation();
    }

    public SourceLocation lastLocation() {
        return lastConsumed != null ? lastConsumed.endLocation() : location();
    }

    public int mark() {
        return cursor;
    }

    public void restore(int mark) {
        this.cursor = mark;
        this.lastConsumed = (mark > 0 && mark - 1 < tokens.size()) ? tokens.get(mark - 1) : null;
    }
}
