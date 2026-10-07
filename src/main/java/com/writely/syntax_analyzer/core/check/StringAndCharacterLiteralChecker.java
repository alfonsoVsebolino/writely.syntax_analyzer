package com.writely.syntax_analyzer.core.check;

import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Syntax checker validating string and character literals across Java, Python, and C++
 * in accordance with Issue #20 and ADR 0007.
 */
public class StringAndCharacterLiteralChecker implements SyntaxChecker {

    public static final String ERR_UNCLOSED_STRING_LITERAL = "ERR_UNCLOSED_STRING_LITERAL";
    public static final String ERR_UNCLOSED_CHARACTER_LITERAL = "ERR_UNCLOSED_CHARACTER_LITERAL";
    public static final String ERR_EMPTY_CHARACTER_LITERAL = "ERR_EMPTY_CHARACTER_LITERAL";
    public static final String ERR_INVALID_CHARACTER_LITERAL = "ERR_INVALID_CHARACTER_LITERAL";
    public static final String ERR_ILLEGAL_ESCAPE_SEQUENCE = "ERR_ILLEGAL_ESCAPE_SEQUENCE";

    public StringAndCharacterLiteralChecker() {
    }

    @Override
    public CheckCategory category() {
        return CheckCategory.LITERAL_SYNTAX;
    }

    @Override
    public List<Diagnostic> check(SourcePayload payload, List<Token> tokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");

        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        Language language = payload.language();
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (Token token : tokens) {
            if (token.isTrivia()) {
                continue;
            }

            TokenType tokenType = token.tokenType();
            if (tokenType == TokenType.LITERAL_STRING) {
                checkStringLiteral(language, token, diagnostics);
            } else if (tokenType == TokenType.LITERAL_CHAR) {
                checkCharacterLiteral(language, token, diagnostics);
            }
        }

        return Collections.unmodifiableList(diagnostics);
    }

    private void checkStringLiteral(Language language, Token token, List<Diagnostic> diagnostics) {
        String lexeme = token.lexeme();
        switch (language) {
            case JAVA -> checkJavaStringLiteral(token, lexeme, diagnostics);
            case PYTHON -> checkPythonStringLiteral(token, lexeme, diagnostics);
            case CPP -> checkCppStringLiteral(token, lexeme, diagnostics);
        }
    }

    private void checkJavaStringLiteral(Token token, String lexeme, List<Diagnostic> diagnostics) {
        // 1. Text blocks: """..."""
        if (lexeme.startsWith("\"\"\"")) {
            boolean closed = lexeme.length() >= 6
                && lexeme.endsWith("\"\"\"")
                && hasEvenPrecedingBackslashes(lexeme, lexeme.length() - 3);

            if (!closed) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.LITERAL_SYNTAX,
                    token.startLocation(),
                    "Unclosed text block literal; missing closing '\"\"\"'",
                    ERR_UNCLOSED_STRING_LITERAL,
                    "Close text block with '\"\"\"'"
                ));
                return;
            }

            validateEscapes(Language.JAVA, token, lexeme, 3, lexeme.length() - 3, diagnostics);
            return;
        }

        // 2. Standard Java string: "..."
        if (!lexeme.startsWith("\"")) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                token.startLocation(),
                "Unclosed string literal; missing opening quote",
                ERR_UNCLOSED_STRING_LITERAL,
                "Insert opening quote '\"'"
            ));
            return;
        }

        boolean closed = lexeme.length() >= 2
            && lexeme.endsWith("\"")
            && hasEvenPrecedingBackslashes(lexeme, lexeme.length() - 1);

        if (!closed) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                token.startLocation(),
                "Unclosed string literal; missing closing quote",
                ERR_UNCLOSED_STRING_LITERAL,
                "Insert closing quote '\"'"
            ));
            return;
        }

        validateEscapes(Language.JAVA, token, lexeme, 1, lexeme.length() - 1, diagnostics);
    }

    private void checkPythonStringLiteral(Token token, String lexeme, List<Diagnostic> diagnostics) {
        int prefixLen = detectPythonPrefixLength(lexeme);
        String quoteDelim = detectPythonQuoteDelimiter(lexeme, prefixLen);

        if (quoteDelim == null) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                token.startLocation(),
                "Unclosed string literal; missing opening quote",
                ERR_UNCLOSED_STRING_LITERAL,
                "Insert opening quote"
            ));
            return;
        }

        boolean isTriple = quoteDelim.length() == 3;
        int minLength = prefixLen + (quoteDelim.length() * 2);

        boolean closed = lexeme.length() >= minLength
            && lexeme.endsWith(quoteDelim)
            && hasEvenPrecedingBackslashes(lexeme, lexeme.length() - quoteDelim.length());

        if (!closed) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                token.startLocation(),
                isTriple
                    ? "Unclosed triple-quoted string literal; missing closing " + quoteDelim
                    : "Unclosed string literal; missing closing quote " + quoteDelim,
                ERR_UNCLOSED_STRING_LITERAL,
                "Insert closing " + quoteDelim
            ));
            return;
        }

        // Raw strings (r or R in prefix) are exempt from escape validation
        String prefix = lexeme.substring(0, prefixLen).toLowerCase(Locale.ROOT);
        boolean isRaw = prefix.contains("r");

        if (!isRaw) {
            validateEscapes(
                Language.PYTHON,
                token,
                lexeme,
                prefixLen + quoteDelim.length(),
                lexeme.length() - quoteDelim.length(),
                diagnostics
            );
        }
    }

    private void checkCppStringLiteral(Token token, String lexeme, List<Diagnostic> diagnostics) {
        // 1. Raw string literals: R"delim(...)delim"
        if (lexeme.startsWith("R\"")) {
            int openParen = lexeme.indexOf('(', 2);
            if (openParen == -1) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.LITERAL_SYNTAX,
                    token.startLocation(),
                    "Unclosed C++ raw string literal; missing '(' after delimiter",
                    ERR_UNCLOSED_STRING_LITERAL,
                    "Provide opening '(' and closing delimiter"
                ));
                return;
            }

            String delim = lexeme.substring(2, openParen);
            String closing = ")" + delim + "\"";

            boolean closed = lexeme.length() >= openParen + 1 + closing.length()
                && lexeme.endsWith(closing);

            if (!closed) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.LITERAL_SYNTAX,
                    token.startLocation(),
                    "Unclosed C++ raw string literal; expected closing '" + closing + "'",
                    ERR_UNCLOSED_STRING_LITERAL,
                    "Close raw string with '" + closing + "'"
                ));
                return;
            }

            // Raw strings in C++ are exempt from escape validation
            return;
        }

        // 2. Standard C++ string: "..."
        int quoteIdx = lexeme.indexOf('"');
        if (quoteIdx == -1) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                token.startLocation(),
                "Unclosed string literal; missing opening quote",
                ERR_UNCLOSED_STRING_LITERAL,
                "Insert opening quote '\"'"
            ));
            return;
        }

        boolean closed = lexeme.length() >= quoteIdx + 2
            && lexeme.endsWith("\"")
            && hasEvenPrecedingBackslashes(lexeme, lexeme.length() - 1);

        if (!closed) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                token.startLocation(),
                "Unclosed string literal; missing closing quote",
                ERR_UNCLOSED_STRING_LITERAL,
                "Insert closing quote '\"'"
            ));
            return;
        }

        validateEscapes(Language.CPP, token, lexeme, quoteIdx + 1, lexeme.length() - 1, diagnostics);
    }

    private void checkCharacterLiteral(Language language, Token token, List<Diagnostic> diagnostics) {
        String lexeme = token.lexeme();
        int quoteIdx = lexeme.indexOf('\'');
        if (quoteIdx == -1) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                token.startLocation(),
                "Unclosed character literal; missing opening single quote",
                ERR_UNCLOSED_CHARACTER_LITERAL,
                "Insert opening single quote '\''"
            ));
            return;
        }

        boolean closed = lexeme.length() >= quoteIdx + 2
            && lexeme.endsWith("'")
            && hasEvenPrecedingBackslashes(lexeme, lexeme.length() - 1);

        if (!closed) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                token.startLocation(),
                "Unclosed character literal; missing closing single quote",
                ERR_UNCLOSED_CHARACTER_LITERAL,
                "Insert closing single quote '\''"
            ));
            return;
        }

        int contentStart = quoteIdx + 1;
        int contentEnd = lexeme.length() - 1;
        int contentLength = contentEnd - contentStart;

        if (contentLength == 0) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                token.startLocation(),
                "Empty character literal",
                ERR_EMPTY_CHARACTER_LITERAL,
                "Specify a character literal or use empty string \"\""
            ));
            return;
        }

        int charCount = 0;
        List<Diagnostic> escapeDiags = new ArrayList<>();
        int i = contentStart;

        while (i < contentEnd) {
            if (lexeme.charAt(i) == '\\') {
                if (i + 1 >= contentEnd) {
                    escapeDiags.add(Diagnostic.error(
                        CheckCategory.LITERAL_SYNTAX,
                        locationAtOffset(token, i),
                        "Illegal trailing escape backslash in character literal",
                        ERR_ILLEGAL_ESCAPE_SEQUENCE,
                        "Remove dangling backslash"
                    ));
                    charCount++;
                    break;
                }
                int consumed = validateEscapeSequence(language, token, lexeme, i, contentEnd, escapeDiags);
                charCount++;
                i += (consumed > 0 ? consumed : 1);
            } else {
                charCount++;
                i++;
            }
        }

        if (!escapeDiags.isEmpty()) {
            diagnostics.addAll(escapeDiags);
        } else if (charCount > 1) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                token.startLocation(),
                "Invalid character literal: contains multiple characters",
                ERR_INVALID_CHARACTER_LITERAL,
                "Use a single character or convert to a string literal"
            ));
        }
    }

    private void validateEscapes(
        Language language,
        Token token,
        String lexeme,
        int contentStart,
        int contentEnd,
        List<Diagnostic> diagnostics
    ) {
        int i = contentStart;
        while (i < contentEnd) {
            if (lexeme.charAt(i) == '\\') {
                int escapeStart = i;
                if (i + 1 >= contentEnd) {
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.LITERAL_SYNTAX,
                        locationAtOffset(token, escapeStart),
                        "Illegal trailing escape backslash in literal",
                        ERR_ILLEGAL_ESCAPE_SEQUENCE,
                        "Remove dangling backslash or complete escape sequence"
                    ));
                    break;
                }

                int consumed = validateEscapeSequence(language, token, lexeme, escapeStart, contentEnd, diagnostics);
                i += (consumed > 0 ? consumed : 1);
            } else {
                i++;
            }
        }
    }

    private int validateEscapeSequence(
        Language language,
        Token token,
        String lexeme,
        int escapeStart,
        int contentEnd,
        List<Diagnostic> diagnostics
    ) {
        return switch (language) {
            case JAVA -> validateJavaEscape(token, lexeme, escapeStart, contentEnd, diagnostics);
            case PYTHON -> validatePythonEscape(token, lexeme, escapeStart, contentEnd, diagnostics);
            case CPP -> validateCppEscape(token, lexeme, escapeStart, contentEnd, diagnostics);
        };
    }

    private int validateJavaEscape(
        Token token,
        String lexeme,
        int escapeStart,
        int contentEnd,
        List<Diagnostic> diagnostics
    ) {
        char next = lexeme.charAt(escapeStart + 1);

        // Standard escapes: \b, \t, \n, \f, \r, \", \', \\, \s
        if (next == 'b' || next == 't' || next == 'n' || next == 'f' || next == 'r'
            || next == '\"' || next == '\'' || next == '\\' || next == 's') {
            return 2;
        }

        // Unicode escape \\uXXXX (supports multiple 'u's, e.g. \\u0041)
        if (next == 'u') {
            int uIdx = escapeStart + 1;
            while (uIdx < contentEnd && lexeme.charAt(uIdx) == 'u') {
                uIdx++;
            }
            if (uIdx + 4 <= contentEnd
                && isHexDigit(lexeme.charAt(uIdx))
                && isHexDigit(lexeme.charAt(uIdx + 1))
                && isHexDigit(lexeme.charAt(uIdx + 2))
                && isHexDigit(lexeme.charAt(uIdx + 3))) {
                return (uIdx + 4) - escapeStart;
            } else {
                int endOfBad = uIdx;
                while (endOfBad < contentEnd && endOfBad < uIdx + 4 && isHexDigit(lexeme.charAt(endOfBad))) {
                    endOfBad++;
                }
                diagnostics.add(Diagnostic.error(
                    CheckCategory.LITERAL_SYNTAX,
                    locationAtOffset(token, escapeStart),
                    "Illegal unicode escape sequence in literal; expected 4 hexadecimal digits",
                    ERR_ILLEGAL_ESCAPE_SEQUENCE,
                    "Provide exactly 4 hexadecimal digits after '\\u'"
                ));
                return Math.max(2, endOfBad - escapeStart);
            }
        }

        // Octal escapes: \0..\377
        if (next >= '0' && next <= '3') {
            if (escapeStart + 2 < contentEnd && isOctalDigit(lexeme.charAt(escapeStart + 2))) {
                if (escapeStart + 3 < contentEnd && isOctalDigit(lexeme.charAt(escapeStart + 3))) {
                    return 4;
                }
                return 3;
            }
            return 2;
        }
        if (next >= '4' && next <= '7') {
            if (escapeStart + 2 < contentEnd && isOctalDigit(lexeme.charAt(escapeStart + 2))) {
                return 3;
            }
            return 2;
        }

        // Illegal escape sequence
        diagnostics.add(Diagnostic.error(
            CheckCategory.LITERAL_SYNTAX,
            locationAtOffset(token, escapeStart),
            String.format("Illegal escape sequence '\\%c' in literal", next),
            ERR_ILLEGAL_ESCAPE_SEQUENCE,
            "Replace with a valid escape sequence or remove backslash"
        ));
        return 2;
    }

    private int validatePythonEscape(
        Token token,
        String lexeme,
        int escapeStart,
        int contentEnd,
        List<Diagnostic> diagnostics
    ) {
        char next = lexeme.charAt(escapeStart + 1);

        // Line continuation
        if (next == '\n') {
            return 2;
        }
        if (next == '\r') {
            if (escapeStart + 2 < contentEnd && lexeme.charAt(escapeStart + 2) == '\n') {
                return 3;
            }
            return 2;
        }

        // Standard escapes
        if (next == '\\' || next == '\'' || next == '\"' || next == 'a' || next == 'b'
            || next == 'f' || next == 'n' || next == 'r' || next == 't' || next == 'v') {
            return 2;
        }

        // Octal escapes: \ooo (1-3 digits)
        if (isOctalDigit(next)) {
            int octalLen = 1;
            if (escapeStart + 2 < contentEnd && isOctalDigit(lexeme.charAt(escapeStart + 2))) {
                octalLen++;
                if (escapeStart + 3 < contentEnd && isOctalDigit(lexeme.charAt(escapeStart + 3))) {
                    octalLen++;
                }
            }
            return 1 + octalLen;
        }

        // Hex escape: \xhh (2 hex digits)
        if (next == 'x') {
            if (escapeStart + 3 < contentEnd
                && isHexDigit(lexeme.charAt(escapeStart + 2))
                && isHexDigit(lexeme.charAt(escapeStart + 3))) {
                return 4;
            }
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                locationAtOffset(token, escapeStart),
                "Illegal hex escape sequence '\\x' in literal; expected 2 hexadecimal digits",
                ERR_ILLEGAL_ESCAPE_SEQUENCE,
                "Provide exactly 2 hexadecimal digits after '\\x'"
            ));
            return 2;
        }

        // Unicode escape: \\uxxxx (4 hex digits)
        if (next == 'u') {
            if (escapeStart + 5 < contentEnd
                && isHexDigit(lexeme.charAt(escapeStart + 2))
                && isHexDigit(lexeme.charAt(escapeStart + 3))
                && isHexDigit(lexeme.charAt(escapeStart + 4))
                && isHexDigit(lexeme.charAt(escapeStart + 5))) {
                return 6;
            }
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                locationAtOffset(token, escapeStart),
                "Illegal unicode escape sequence '\\u' in literal; expected 4 hexadecimal digits",
                ERR_ILLEGAL_ESCAPE_SEQUENCE,
                "Provide exactly 4 hexadecimal digits after '\\u'"
            ));
            return 2;
        }

        // Unicode escape: \Uxxxxxxxx (8 hex digits)
        if (next == 'U') {
            boolean valid = (escapeStart + 9 < contentEnd);
            if (valid) {
                for (int k = 0; k < 8; k++) {
                    if (!isHexDigit(lexeme.charAt(escapeStart + 2 + k))) {
                        valid = false;
                        break;
                    }
                }
            }
            if (valid) {
                return 10;
            }
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                locationAtOffset(token, escapeStart),
                "Illegal unicode escape sequence '\\U' in literal; expected 8 hexadecimal digits",
                ERR_ILLEGAL_ESCAPE_SEQUENCE,
                "Provide exactly 8 hexadecimal digits after '\\U'"
            ));
            return 2;
        }

        // Named unicode: \N{name}
        if (next == 'N') {
            if (escapeStart + 2 < contentEnd && lexeme.charAt(escapeStart + 2) == '{') {
                int closeIdx = lexeme.indexOf('}', escapeStart + 3);
                if (closeIdx != -1 && closeIdx < contentEnd) {
                    return (closeIdx + 1) - escapeStart;
                }
            }
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                locationAtOffset(token, escapeStart),
                "Illegal named unicode escape sequence '\\N{...}' in literal",
                ERR_ILLEGAL_ESCAPE_SEQUENCE,
                "Provide a valid Unicode character name in braces '\\N{name}'"
            ));
            return 2;
        }

        // Illegal escape
        diagnostics.add(Diagnostic.error(
            CheckCategory.LITERAL_SYNTAX,
            locationAtOffset(token, escapeStart),
            String.format("Illegal escape sequence '\\%c' in literal", next),
            ERR_ILLEGAL_ESCAPE_SEQUENCE,
            "Replace with a valid escape sequence or remove backslash"
        ));
        return 2;
    }

    private int validateCppEscape(
        Token token,
        String lexeme,
        int escapeStart,
        int contentEnd,
        List<Diagnostic> diagnostics
    ) {
        char next = lexeme.charAt(escapeStart + 1);

        // Line continuation
        if (next == '\n') {
            return 2;
        }
        if (next == '\r') {
            if (escapeStart + 2 < contentEnd && lexeme.charAt(escapeStart + 2) == '\n') {
                return 3;
            }
            return 2;
        }

        // Simple escapes: \', \", \?, \\, \a, \b, \f, \n, \r, \t, \v
        if (next == '\'' || next == '\"' || next == '?' || next == '\\'
            || next == 'a' || next == 'b' || next == 'f' || next == 'n'
            || next == 'r' || next == 't' || next == 'v') {
            return 2;
        }

        // Octal escapes: 1-3 digits
        if (isOctalDigit(next)) {
            int octalLen = 1;
            if (escapeStart + 2 < contentEnd && isOctalDigit(lexeme.charAt(escapeStart + 2))) {
                octalLen++;
                if (escapeStart + 3 < contentEnd && isOctalDigit(lexeme.charAt(escapeStart + 3))) {
                    octalLen++;
                }
            }
            return 1 + octalLen;
        }

        // Hex escape: \x followed by one or more hex digits
        if (next == 'x') {
            if (escapeStart + 2 < contentEnd && isHexDigit(lexeme.charAt(escapeStart + 2))) {
                int hexLen = 1;
                while (escapeStart + 2 + hexLen < contentEnd && isHexDigit(lexeme.charAt(escapeStart + 2 + hexLen))) {
                    hexLen++;
                }
                return 2 + hexLen;
            }
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                locationAtOffset(token, escapeStart),
                "Illegal hex escape sequence '\\x' in literal; expected at least one hexadecimal digit",
                ERR_ILLEGAL_ESCAPE_SEQUENCE,
                "Provide one or more hexadecimal digits after '\\x'"
            ));
            return 2;
        }

        // Universal character name: \\uXXXX (4 hex digits)
        if (next == 'u') {
            if (escapeStart + 5 < contentEnd
                && isHexDigit(lexeme.charAt(escapeStart + 2))
                && isHexDigit(lexeme.charAt(escapeStart + 3))
                && isHexDigit(lexeme.charAt(escapeStart + 4))
                && isHexDigit(lexeme.charAt(escapeStart + 5))) {
                return 6;
            }
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                locationAtOffset(token, escapeStart),
                "Illegal universal character name '\\u' in literal; expected 4 hexadecimal digits",
                ERR_ILLEGAL_ESCAPE_SEQUENCE,
                "Provide exactly 4 hexadecimal digits after '\\u'"
            ));
            return 2;
        }

        // Universal character name: \UXXXXXXXX (8 hex digits)
        if (next == 'U') {
            boolean valid = (escapeStart + 9 < contentEnd);
            if (valid) {
                for (int k = 0; k < 8; k++) {
                    if (!isHexDigit(lexeme.charAt(escapeStart + 2 + k))) {
                        valid = false;
                        break;
                    }
                }
            }
            if (valid) {
                return 10;
            }
            diagnostics.add(Diagnostic.error(
                CheckCategory.LITERAL_SYNTAX,
                locationAtOffset(token, escapeStart),
                "Illegal universal character name '\\U' in literal; expected 8 hexadecimal digits",
                ERR_ILLEGAL_ESCAPE_SEQUENCE,
                "Provide exactly 8 hexadecimal digits after '\\U'"
            ));
            return 2;
        }

        // Illegal escape
        diagnostics.add(Diagnostic.error(
            CheckCategory.LITERAL_SYNTAX,
            locationAtOffset(token, escapeStart),
            String.format("Illegal escape sequence '\\%c' in literal", next),
            ERR_ILLEGAL_ESCAPE_SEQUENCE,
            "Replace with a valid escape sequence or remove backslash"
        ));
        return 2;
    }

    private SourceLocation locationAtOffset(Token token, int offsetInLexeme) {
        if (offsetInLexeme <= 0) {
            return token.startLocation();
        }
        String lexeme = token.lexeme();
        int limit = Math.min(offsetInLexeme, lexeme.length());
        int line = token.startLocation().line();
        int column = token.startLocation().column();
        int charOffset = token.startLocation().charOffset() + limit;

        for (int i = 0; i < limit; i++) {
            char ch = lexeme.charAt(i);
            if (ch == '\n') {
                line++;
                column = 1;
            } else if (ch == '\r') {
                if (i + 1 < limit && lexeme.charAt(i + 1) == '\n') {
                    // Handled by subsequent '\n'
                } else {
                    line++;
                    column = 1;
                }
            } else {
                column++;
            }
        }
        return SourceLocation.of(line, column, charOffset);
    }

    private static boolean hasEvenPrecedingBackslashes(String text, int index) {
        int backslashes = 0;
        int i = index - 1;
        while (i >= 0 && text.charAt(i) == '\\') {
            backslashes++;
            i--;
        }
        return (backslashes % 2) == 0;
    }

    private static int detectPythonPrefixLength(String lexeme) {
        if (lexeme.startsWith("\"\"\"") || lexeme.startsWith("'''")
            || lexeme.startsWith("\"") || lexeme.startsWith("'")) {
            return 0;
        }
        if (lexeme.length() >= 3) {
            String p2 = lexeme.substring(0, 2).toLowerCase(Locale.ROOT);
            if (p2.equals("rf") || p2.equals("fr") || p2.equals("rb") || p2.equals("br")) {
                char c2 = lexeme.charAt(2);
                if (c2 == '\"' || c2 == '\'') {
                    return 2;
                }
            }
        }
        if (lexeme.length() >= 2) {
            char c0 = Character.toLowerCase(lexeme.charAt(0));
            if (c0 == 'r' || c0 == 'u' || c0 == 'f' || c0 == 'b') {
                char c1 = lexeme.charAt(1);
                if (c1 == '\"' || c1 == '\'') {
                    return 1;
                }
            }
        }
        return 0;
    }

    private static String detectPythonQuoteDelimiter(String lexeme, int offset) {
        if (lexeme.startsWith("\"\"\"", offset)) return "\"\"\"";
        if (lexeme.startsWith("'''", offset)) return "'''";
        if (lexeme.startsWith("\"", offset)) return "\"";
        if (lexeme.startsWith("'", offset)) return "'";
        return null;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isOctalDigit(char c) {
        return c >= '0' && c <= '7';
    }
}
