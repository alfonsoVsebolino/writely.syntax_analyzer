package com.writely.syntax_analyzer.adapter.java;

import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Token stream navigation and lookahead helper with non-cascading diagnostic recording.
 */
public class JavaTokenStream {

    private static final Set<String> STATEMENT_STARTERS = Set.of(
        "if", "for", "while", "do", "switch", "return", "throw", "break",
        "continue", "yield", "try", "assert", "synchronized", "class",
        "interface", "record", "enum", "public", "protected", "private",
        "static", "final", "abstract", "default", "int", "boolean", "char",
        "byte", "short", "long", "float", "double", "void", "var"
    );

    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final SourceLocation defaultLocation;
    private int cursor = 0;
    private Token lastConsumed = null;

    public JavaTokenStream(List<Token> allTokens) {
        Objects.requireNonNull(allTokens, "allTokens must not be null");
        List<Token> filtered = new ArrayList<>();
        for (Token t : allTokens) {
            if (!t.isTrivia()) {
                filtered.add(t);
            }
        }
        this.tokens = new ArrayList<>(filtered);
        this.defaultLocation = allTokens.isEmpty() ? SourceLocation.start() : allTokens.get(0).startLocation();
    }

    public boolean hasMore() {
        return cursor < tokens.size();
    }

    public int position() {
        return cursor;
    }

    public void seek(int position) {
        this.cursor = position;
    }

    public Token peek() {
        if (cursor < tokens.size()) {
            return tokens.get(cursor);
        }
        return eofToken();
    }

    public Token peek(int offset) {
        int idx = cursor + offset;
        if (idx >= 0 && idx < tokens.size()) {
            return tokens.get(idx);
        }
        return eofToken();
    }

    public Token consume() {
        if (cursor < tokens.size()) {
            Token t = tokens.get(cursor++);
            lastConsumed = t;
            return t;
        }
        return eofToken();
    }

    public boolean check(TokenType type) {
        return hasMore() && peek().tokenType() == type;
    }

    public boolean check(TokenType type, String lexeme) {
        if (">".equals(lexeme)) {
            decomposeGreaterIfNeeded();
        }
        return check(type) && peek().lexeme().equals(lexeme);
    }

    public boolean check(String lexeme) {
        if (">".equals(lexeme)) {
            decomposeGreaterIfNeeded();
        }
        return hasMore() && peek().lexeme().equals(lexeme);
    }

    public boolean match(TokenType type, String lexeme) {
        if (">".equals(lexeme)) {
            decomposeGreaterIfNeeded();
        }
        if (check(type, lexeme)) {
            consume();
            return true;
        }
        return false;
    }

    public boolean match(String lexeme) {
        if (">".equals(lexeme)) {
            decomposeGreaterIfNeeded();
        }
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

    public Token expect(
        String expectedLexeme,
        CheckCategory category,
        String errorCode,
        String errorMessage,
        String suggestedFix
    ) {
        if (">".equals(expectedLexeme)) {
            decomposeGreaterIfNeeded();
        }

        if (check(expectedLexeme)) {
            return consume();
        }

        // For missing semicolon, report location at end of previous token
        SourceLocation loc = ";".equals(expectedLexeme) ? previousEndLocation() : currentLocation();
        addDiagnostic(Diagnostic.error(category, loc, errorMessage, errorCode, suggestedFix));

        // Recovery: if expecting semicolon or delimiter and current token is a boundary, do not consume it
        if (";".equals(expectedLexeme)) {
            if (!hasMore() || check("}") || check(";") || isStatementStarter(peek())
                || (hasMore() && peek().startLocation().line() > loc.line())
                || (hasMore() && isPossibleStatementStart(peek()))) {
                return syntheticToken(expectedLexeme, loc);
            }
        } else if (")".equals(expectedLexeme) || "}".equals(expectedLexeme) || "]".equals(expectedLexeme)) {
            if (!hasMore() || check(";") || check("}") || check("{")) {
                return syntheticToken(expectedLexeme, loc);
            }
        }

        // If not at a clean boundary, consume one unexpected token to advance
        if (hasMore()) {
            return consume();
        }
        return syntheticToken(expectedLexeme, loc);
    }

    public Token expect(
        TokenType type,
        CheckCategory category,
        String errorCode,
        String errorMessage,
        String suggestedFix
    ) {
        if (check(type)) {
            return consume();
        }

        SourceLocation loc = currentLocation();
        addDiagnostic(Diagnostic.error(category, loc, errorMessage, errorCode, suggestedFix));

        if (hasMore() && peek().tokenType() != TokenType.DELIMITER) {
            return consume();
        }
        return syntheticToken("<missing>", loc);
    }

    public void addDiagnostic(Diagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic must not be null");
        this.diagnostics.add(diagnostic);
    }

    public List<Diagnostic> diagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public SourceLocation currentLocation() {
        if (hasMore()) {
            return peek().startLocation();
        }
        return previousEndLocation();
    }

    public SourceLocation previousEndLocation() {
        if (lastConsumed != null) {
            return lastConsumed.endLocation();
        }
        if (!tokens.isEmpty()) {
            return tokens.get(tokens.size() - 1).endLocation();
        }
        return defaultLocation;
    }

    public Token lastConsumed() {
        return lastConsumed;
    }

    private void decomposeGreaterIfNeeded() {
        if (!hasMore()) return;
        Token cur = tokens.get(cursor);
        String lex = cur.lexeme();
        if (">>".equals(lex)) {
            SourceLocation mid = SourceLocation.of(
                cur.startLocation().line(),
                cur.startLocation().column() + 1,
                cur.startLocation().charOffset() + 1
            );
            Token first = Token.of(TokenType.DELIMITER, ">", SourceSpan.of(cur.startLocation(), mid), "DELIMITER");
            Token second = Token.of(TokenType.DELIMITER, ">", SourceSpan.of(mid, cur.endLocation()), "DELIMITER");
            tokens.set(cursor, first);
            tokens.add(cursor + 1, second);
        } else if (">>>".equals(lex)) {
            SourceLocation mid1 = SourceLocation.of(
                cur.startLocation().line(),
                cur.startLocation().column() + 1,
                cur.startLocation().charOffset() + 1
            );
            Token first = Token.of(TokenType.DELIMITER, ">", SourceSpan.of(cur.startLocation(), mid1), "DELIMITER");
            Token rem = Token.of(TokenType.DELIMITER, ">>", SourceSpan.of(mid1, cur.endLocation()), "DELIMITER");
            tokens.set(cursor, first);
            tokens.add(cursor + 1, rem);
        }
    }

    private boolean isStatementStarter(Token t) {
        return t.tokenType() == TokenType.KEYWORD && STATEMENT_STARTERS.contains(t.lexeme());
    }

    private boolean isPossibleStatementStart(Token t) {
        if (t.tokenType() == TokenType.IDENTIFIER) {
            Token next = peek(1);
            String nextLex = next.lexeme();
            return "=".equals(nextLex) || "+=".equals(nextLex) || "-=".equals(nextLex)
                || "*=".equals(nextLex) || "/=".equals(nextLex) || "(".equals(nextLex)
                || ".".equals(nextLex) || next.tokenType() == TokenType.IDENTIFIER;
        }
        return false;
    }

    private Token eofToken() {
        SourceLocation loc = previousEndLocation();
        return Token.of(TokenType.UNKNOWN, "<EOF>", SourceSpan.point(loc), "EOF");
    }

    private Token syntheticToken(String lexeme, SourceLocation location) {
        return Token.of(TokenType.UNKNOWN, lexeme, SourceSpan.point(location), "SYNTHETIC");
    }
}
