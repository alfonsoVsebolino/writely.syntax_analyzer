package com.writely.syntax_analyzer.core.tokenization;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Tokenizer for Python adhering to ADR 0004 with indentation tracking stack,
 * INDENT/DEDENT/NEWLINE emissions, triple-quoted strings, and Python operators.
 */
public class PythonTokenizer implements Tokenizer {

    private static final Set<String> KEYWORDS = Set.of(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
        "while", "with", "yield", "match", "case"
    );

    private static final List<String> OPERATORS = List.of(
        "**=", "//=", "<<=", ">>=",
        "**", "//", ":=", "<<", ">>", "<=", ">=", "==", "!=",
        "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
        "+", "-", "*", "/", "%", "@", "&", "|", "^", "~",
        "<", ">", "="
    );

    @Override
    public List<Token> tokenize(SourcePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        if (payload.language() != Language.PYTHON) {
            throw new IllegalArgumentException("PythonTokenizer only supports Python, but got: " + payload.language());
        }

        String source = payload.sourceText();
        if (source.isEmpty()) {
            return Collections.emptyList();
        }

        SourceCharStream stream = new SourceCharStream(source);
        List<Token> tokens = new ArrayList<>();

        Deque<Integer> indentStack = new ArrayDeque<>();
        indentStack.push(0);

        int bracketDepth = 0;
        boolean atLineStart = true;
        boolean lineHasTokens = false;

        while (stream.hasMore()) {
            // 1. Indentation handling at the start of a logical line
            if (atLineStart) {
                if (bracketDepth > 0) {
                    atLineStart = false;
                } else {
                    SourceLocation lineStartLoc = stream.location();
                    int currentIndent = 0;
                    StringBuilder indentLexeme = new StringBuilder();

                    while (stream.hasMore() && (stream.peek() == ' ' || stream.peek() == '\t')) {
                        char ws = stream.consume();
                        indentLexeme.append(ws);
                        currentIndent++;
                    }

                    // Check if blank line
                    if (stream.peek() == '\r' || stream.peek() == '\n') {
                        if (stream.peek() == '\r') stream.consume();
                        if (stream.peek() == '\n') stream.consume();
                        continue; // Still at line start of subsequent line
                    }

                    // Check if comment-only line
                    if (stream.peek() == '#') {
                        SourceLocation commentStart = stream.location();
                        while (stream.hasMore() && stream.peek() != '\n' && stream.peek() != '\r') {
                            stream.consume();
                        }
                        SourceSpan span = SourceSpan.of(commentStart, stream.location());
                        String lexeme = stream.substring(commentStart.charOffset(), stream.charOffset());
                        tokens.add(Token.of(TokenType.COMMENT, lexeme, span, "COMMENT"));

                        if (stream.peek() == '\r') stream.consume();
                        if (stream.peek() == '\n') stream.consume();
                        continue; // Still at line start of subsequent line
                    }

                    if (stream.isEof()) {
                        break;
                    }

                    // Non-empty line with actual code: compare with indentation stack
                    int currentStackTop = indentStack.peek();
                    if (currentIndent > currentStackTop) {
                        indentStack.push(currentIndent);
                        SourceSpan indentSpan = SourceSpan.of(lineStartLoc, stream.location());
                        tokens.add(Token.of(TokenType.INDENT, indentLexeme.toString(), indentSpan, "INDENT"));
                    } else if (currentIndent < currentStackTop) {
                        while (!indentStack.isEmpty() && indentStack.peek() > currentIndent) {
                            indentStack.pop();
                            SourceSpan dedentSpan = SourceSpan.point(lineStartLoc);
                            tokens.add(Token.of(TokenType.DEDENT, "", dedentSpan, "DEDENT"));
                        }
                        if (indentStack.isEmpty() || indentStack.peek() != currentIndent) {
                            indentStack.push(currentIndent);
                        }
                    }

                    atLineStart = false;
                    lineHasTokens = false;
                }
            }

            char c = stream.peek();

            // 2. Whitespace inside line (spaces and tabs)
            if (c == ' ' || c == '\t') {
                stream.consume();
                continue;
            }

            // 3. Explicit line continuation with backslash \
            if (c == '\\' && (stream.peek(1) == '\n' || (stream.peek(1) == '\r' && stream.peek(2) == '\n') || stream.peek(1) == '\r')) {
                stream.consume(); // \
                if (stream.peek() == '\r') stream.consume();
                if (stream.peek() == '\n') stream.consume();
                continue;
            }

            // 4. Line terminators
            if (c == '\r' || c == '\n') {
                SourceLocation start = stream.location();
                if (stream.peek() == '\r') stream.consume();
                if (stream.peek() == '\n') stream.consume();

                if (bracketDepth == 0 && lineHasTokens) {
                    SourceSpan span = SourceSpan.of(start, stream.location());
                    String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                    tokens.add(Token.of(TokenType.NEWLINE, lexeme, span, "NEWLINE"));
                }
                atLineStart = true;
                lineHasTokens = false;
                continue;
            }

            // 5. Comments #
            if (c == '#') {
                SourceLocation start = stream.location();
                while (stream.hasMore() && stream.peek() != '\n' && stream.peek() != '\r') {
                    stream.consume();
                }
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.COMMENT, lexeme, span, "COMMENT"));
                continue;
            }

            // 6. String Literals (single, double, triple-quoted, with optional prefix)
            int prefixLen = detectStringPrefix(stream);
            if (prefixLen >= 0) {
                SourceLocation start = stream.location();
                stream.advance(prefixLen);

                String quoteDelim = detectQuoteDelimiter(stream);
                if (quoteDelim != null) {
                    stream.advance(quoteDelim.length());
                    boolean isTriple = quoteDelim.length() == 3;

                    while (stream.hasMore()) {
                        if (stream.startsWith(quoteDelim)) {
                            stream.advance(quoteDelim.length());
                            break;
                        }
                        if (!isTriple && (stream.peek() == '\n' || stream.peek() == '\r')) {
                            // Unterminated single-line string literal
                            break;
                        }
                        if (stream.peek() == '\\') {
                            stream.consume();
                            if (stream.hasMore()) {
                                stream.consume();
                            }
                        } else {
                            stream.consume();
                        }
                    }

                    SourceSpan span = SourceSpan.of(start, stream.location());
                    String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                    tokens.add(Token.of(TokenType.LITERAL_STRING, lexeme, span, "LITERAL_STRING"));
                    lineHasTokens = true;
                    continue;
                }
            }

            // 7. Numeric Literals
            if (Character.isDigit(c) || (c == '.' && Character.isDigit(stream.peek(1)))) {
                SourceLocation start = stream.location();
                scanNumericLiteral(stream);
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.LITERAL_NUMBER, lexeme, span, "LITERAL_NUMBER"));
                lineHasTokens = true;
                continue;
            }

            // 8. Identifiers and Keywords
            if (isIdentifierStart(c)) {
                SourceLocation start = stream.location();
                StringBuilder sb = new StringBuilder();
                while (stream.hasMore() && isIdentifierPart(stream.peek())) {
                    sb.append(stream.consume());
                }
                String lexeme = sb.toString();
                SourceSpan span = SourceSpan.of(start, stream.location());

                if (KEYWORDS.contains(lexeme)) {
                    tokens.add(Token.of(TokenType.KEYWORD, lexeme, span, "KEYWORD"));
                } else {
                    tokens.add(Token.of(TokenType.IDENTIFIER, lexeme, span, "IDENTIFIER"));
                }
                lineHasTokens = true;
                continue;
            }

            // 9. Delimiters: multi-char ->
            if (stream.startsWith("->")) {
                SourceLocation start = stream.location();
                stream.advance(2);
                SourceSpan span = SourceSpan.of(start, stream.location());
                tokens.add(Token.of(TokenType.DELIMITER, "->", span, "DELIMITER"));
                lineHasTokens = true;
                continue;
            }

            // 10. Operators (longest match first, e.g. :=, **, //=)
            String matchedOp = null;
            for (String op : OPERATORS) {
                if (stream.startsWith(op)) {
                    matchedOp = op;
                    break;
                }
            }
            if (matchedOp != null) {
                SourceLocation start = stream.location();
                stream.advance(matchedOp.length());
                SourceSpan span = SourceSpan.of(start, stream.location());
                tokens.add(Token.of(TokenType.OPERATOR, matchedOp, span, "OPERATOR"));
                lineHasTokens = true;
                continue;
            }

            // 11. Delimiters: single character
            if (isDelimiter(c)) {
                SourceLocation start = stream.location();
                char delim = stream.consume();
                if (delim == '(' || delim == '[' || delim == '{') {
                    bracketDepth++;
                } else if (delim == ')' || delim == ']' || delim == '}') {
                    if (bracketDepth > 0) {
                        bracketDepth--;
                    }
                }
                SourceSpan span = SourceSpan.of(start, stream.location());
                tokens.add(Token.of(TokenType.DELIMITER, String.valueOf(delim), span, "DELIMITER"));
                lineHasTokens = true;
                continue;
            }

            // 11. Unknown characters
            SourceLocation start = stream.location();
            char unk = stream.consume();
            SourceSpan span = SourceSpan.of(start, stream.location());
            tokens.add(Token.of(TokenType.UNKNOWN, String.valueOf(unk), span, "UNKNOWN"));
            lineHasTokens = true;
        }

        // Emit final NEWLINE if logical line had tokens
        if (lineHasTokens && bracketDepth == 0) {
            SourceSpan eofSpan = SourceSpan.point(stream.location());
            tokens.add(Token.of(TokenType.NEWLINE, "\n", eofSpan, "NEWLINE"));
        }

        // Emit DEDENT for any remaining nested indentation levels
        while (indentStack.size() > 1) {
            indentStack.pop();
            SourceSpan eofSpan = SourceSpan.point(stream.location());
            tokens.add(Token.of(TokenType.DEDENT, "", eofSpan, "DEDENT"));
        }

        return Collections.unmodifiableList(tokens);
    }

    private int detectStringPrefix(SourceCharStream stream) {
        char c0 = stream.peek();
        if (c0 == '"' || c0 == '\'') {
            return 0;
        }
        char c1 = stream.peek(1);
        char c2 = stream.peek(2);

        // 2-char prefix like rf, fr, rb, br
        String prefix2 = ("" + c0 + c1).toLowerCase(Locale.ROOT);
        if ((prefix2.equals("rf") || prefix2.equals("fr") || prefix2.equals("rb") || prefix2.equals("br"))
            && (c2 == '"' || c2 == '\'')) {
            return 2;
        }

        // 1-char prefix like r, u, f, b
        char lower0 = Character.toLowerCase(c0);
        if ((lower0 == 'r' || lower0 == 'u' || lower0 == 'f' || lower0 == 'b')
            && (c1 == '"' || c1 == '\'')) {
            return 1;
        }

        return -1;
    }

    private String detectQuoteDelimiter(SourceCharStream stream) {
        if (stream.startsWith("\"\"\"")) {
            return "\"\"\"";
        }
        if (stream.startsWith("'''")) {
            return "'''";
        }
        if (stream.startsWith("\"")) {
            return "\"";
        }
        if (stream.startsWith("'")) {
            return "'";
        }
        return null;
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private boolean isDelimiter(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']'
            || c == '{' || c == '}' || c == ':' || c == ','
            || c == '.' || c == ';';
    }

    private void scanNumericLiteral(SourceCharStream stream) {
        if (stream.peek() == '0' && (stream.peek(1) == 'x' || stream.peek(1) == 'X')) {
            stream.advance(2);
            while (stream.hasMore() && (isHexDigit(stream.peek()) || stream.peek() == '_')) {
                stream.consume();
            }
            return;
        }

        if (stream.peek() == '0' && (stream.peek(1) == 'o' || stream.peek(1) == 'O')) {
            stream.advance(2);
            while (stream.hasMore() && (isOctalDigit(stream.peek()) || stream.peek() == '_')) {
                stream.consume();
            }
            return;
        }

        if (stream.peek() == '0' && (stream.peek(1) == 'b' || stream.peek(1) == 'B')) {
            stream.advance(2);
            while (stream.hasMore() && (stream.peek() == '0' || stream.peek() == '1' || stream.peek() == '_')) {
                stream.consume();
            }
            return;
        }

        while (stream.hasMore() && (Character.isDigit(stream.peek()) || stream.peek() == '_')) {
            stream.consume();
        }
        if (stream.peek() == '.' && stream.peek(1) != '.') {
            stream.consume();
            while (stream.hasMore() && (Character.isDigit(stream.peek()) || stream.peek() == '_')) {
                stream.consume();
            }
        }
        if (stream.peek() == 'e' || stream.peek() == 'E') {
            stream.consume();
            if (stream.peek() == '+' || stream.peek() == '-') {
                stream.consume();
            }
            while (stream.hasMore() && (Character.isDigit(stream.peek()) || stream.peek() == '_')) {
                stream.consume();
            }
        }
        if (stream.peek() == 'j' || stream.peek() == 'J') {
            stream.consume();
        }
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private boolean isOctalDigit(char c) {
        return c >= '0' && c <= '7';
    }
}
