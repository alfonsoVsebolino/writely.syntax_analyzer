package com.writely.syntax_analyzer.core.tokenization;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tokenizer for the Java programming language adhering to ADR 0004.
 */
public class JavaTokenizer implements Tokenizer {

    private static final Set<String> KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "record", "sealed",
        "permits", "non-sealed", "var", "yield"
    );

    private static final List<String> OPERATORS = List.of(
        ">>>=", ">>>", ">>=", "<<=",
        "++", "--", "==", "!=", "<=", ">=", "&&", "||",
        "<<", ">>", "+=", "-=", "*=", "/=", "%=", "&=",
        "|=", "^=", "->", "::",
        "=", "+", "-", "*", "/", "%", "<", ">", "!",
        "&", "|", "^", "~", "?"
    );

    private final boolean preserveWhitespace;

    public JavaTokenizer() {
        this(false);
    }

    public JavaTokenizer(boolean preserveWhitespace) {
        this.preserveWhitespace = preserveWhitespace;
    }

    @Override
    public List<Token> tokenize(SourcePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        if (payload.language() != Language.JAVA) {
            throw new IllegalArgumentException("JavaTokenizer only supports Java, but got: " + payload.language());
        }

        String source = payload.sourceText();
        if (source.isEmpty()) {
            return Collections.emptyList();
        }

        SourceCharStream stream = new SourceCharStream(source);
        List<Token> tokens = new ArrayList<>();

        while (stream.hasMore()) {
            char c = stream.peek();

            // 1. Whitespace
            if (Character.isWhitespace(c)) {
                SourceLocation start = stream.location();
                StringBuilder sb = new StringBuilder();
                while (stream.hasMore() && Character.isWhitespace(stream.peek())) {
                    sb.append(stream.consume());
                }
                if (preserveWhitespace) {
                    SourceSpan span = SourceSpan.of(start, stream.location());
                    tokens.add(Token.of(TokenType.WHITESPACE, sb.toString(), span, "WHITESPACE"));
                }
                continue;
            }

            // 2. Comments
            if (stream.startsWith("//")) {
                SourceLocation start = stream.location();
                stream.consume();
                stream.consume();
                while (stream.hasMore() && stream.peek() != '\n' && stream.peek() != '\r') {
                    stream.consume();
                }
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.COMMENT, lexeme, span, "COMMENT"));
                continue;
            }

            if (stream.startsWith("/*")) {
                SourceLocation start = stream.location();
                stream.consume();
                stream.consume();
                while (stream.hasMore()) {
                    if (stream.startsWith("*/")) {
                        stream.consume();
                        stream.consume();
                        break;
                    }
                    stream.consume();
                }
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.COMMENT, lexeme, span, "COMMENT"));
                continue;
            }

            // 3. String Literals (Text blocks """...""" and standard "...")
            if (stream.startsWith("\"\"\"")) {
                SourceLocation start = stream.location();
                stream.advance(3);
                while (stream.hasMore()) {
                    if (stream.startsWith("\"\"\"")) {
                        stream.advance(3);
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
                continue;
            }

            if (c == '"') {
                SourceLocation start = stream.location();
                stream.consume(); // opening quote
                while (stream.hasMore() && stream.peek() != '\n' && stream.peek() != '\r') {
                    if (stream.peek() == '"') {
                        stream.consume(); // closing quote
                        break;
                    }
                    if (stream.peek() == '\\') {
                        stream.consume();
                        if (stream.hasMore() && stream.peek() != '\n' && stream.peek() != '\r') {
                            stream.consume();
                        }
                    } else {
                        stream.consume();
                    }
                }
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.LITERAL_STRING, lexeme, span, "LITERAL_STRING"));
                continue;
            }

            // 4. Character Literals '...'
            if (c == '\'') {
                SourceLocation start = stream.location();
                stream.consume(); // opening quote
                while (stream.hasMore() && stream.peek() != '\n' && stream.peek() != '\r') {
                    if (stream.peek() == '\'') {
                        stream.consume(); // closing quote
                        break;
                    }
                    if (stream.peek() == '\\') {
                        stream.consume();
                        if (stream.hasMore() && stream.peek() != '\n' && stream.peek() != '\r') {
                            stream.consume();
                        }
                    } else {
                        stream.consume();
                    }
                }
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.LITERAL_CHAR, lexeme, span, "LITERAL_CHAR"));
                continue;
            }

            // 5. Numeric Literals
            if (Character.isDigit(c) || (c == '.' && Character.isDigit(stream.peek(1)))) {
                SourceLocation start = stream.location();
                scanNumericLiteral(stream);
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.LITERAL_NUMBER, lexeme, span, "LITERAL_NUMBER"));
                continue;
            }

            // 6. Identifiers and Keywords
            if (Character.isJavaIdentifierStart(c)) {
                SourceLocation start = stream.location();
                StringBuilder sb = new StringBuilder();
                while (stream.hasMore() && Character.isJavaIdentifierPart(stream.peek())) {
                    sb.append(stream.consume());
                }
                String lexeme = sb.toString();

                // Check for contextual keyword non-sealed
                if ("non".equals(lexeme) && stream.startsWith("-sealed")) {
                    char afterSealed = stream.peek(7);
                    if (!Character.isJavaIdentifierPart(afterSealed)) {
                        stream.advance(7);
                        lexeme = "non-sealed";
                    }
                }

                SourceSpan span = SourceSpan.of(start, stream.location());
                if (KEYWORDS.contains(lexeme)) {
                    tokens.add(Token.of(TokenType.KEYWORD, lexeme, span, "KEYWORD"));
                } else if ("true".equals(lexeme) || "false".equals(lexeme)) {
                    tokens.add(Token.of(TokenType.KEYWORD, lexeme, span, "BOOLEAN_LITERAL"));
                } else if ("null".equals(lexeme)) {
                    tokens.add(Token.of(TokenType.KEYWORD, lexeme, span, "NULL_LITERAL"));
                } else {
                    tokens.add(Token.of(TokenType.IDENTIFIER, lexeme, span, "IDENTIFIER"));
                }
                continue;
            }

            // 7. Operators (longest match first)
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
                continue;
            }

            // 8. Delimiters
            if (stream.startsWith("...")) {
                SourceLocation start = stream.location();
                stream.advance(3);
                SourceSpan span = SourceSpan.of(start, stream.location());
                tokens.add(Token.of(TokenType.DELIMITER, "...", span, "DELIMITER"));
                continue;
            }
            if (isDelimiter(c)) {
                SourceLocation start = stream.location();
                char delim = stream.consume();
                SourceSpan span = SourceSpan.of(start, stream.location());
                tokens.add(Token.of(TokenType.DELIMITER, String.valueOf(delim), span, "DELIMITER"));
                continue;
            }

            // 9. Unknown / Malformed tokens
            SourceLocation start = stream.location();
            char unk = stream.consume();
            SourceSpan span = SourceSpan.of(start, stream.location());
            tokens.add(Token.of(TokenType.UNKNOWN, String.valueOf(unk), span, "UNKNOWN"));
        }

        return Collections.unmodifiableList(tokens);
    }

    private boolean isDelimiter(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']'
            || c == '{' || c == '}' || c == ';' || c == ','
            || c == '.' || c == '@';
    }

    private void scanNumericLiteral(SourceCharStream stream) {
        if (stream.peek() == '0' && (stream.peek(1) == 'x' || stream.peek(1) == 'X')) {
            // Hexadecimal literal
            stream.advance(2);
            while (stream.hasMore() && (isHexDigit(stream.peek()) || stream.peek() == '_')) {
                stream.consume();
            }
            if (stream.peek() == '.') {
                stream.consume();
                while (stream.hasMore() && (isHexDigit(stream.peek()) || stream.peek() == '_')) {
                    stream.consume();
                }
            }
            if (stream.peek() == 'p' || stream.peek() == 'P') {
                stream.consume();
                if (stream.peek() == '+' || stream.peek() == '-') {
                    stream.consume();
                }
                while (stream.hasMore() && (Character.isDigit(stream.peek()) || stream.peek() == '_')) {
                    stream.consume();
                }
            }
            consumeNumericSuffix(stream);
            return;
        }

        if (stream.peek() == '0' && (stream.peek(1) == 'b' || stream.peek(1) == 'B')) {
            // Binary literal
            stream.advance(2);
            while (stream.hasMore() && (stream.peek() == '0' || stream.peek() == '1' || stream.peek() == '_')) {
                stream.consume();
            }
            consumeNumericSuffix(stream);
            return;
        }

        // Decimal / Floating-point / Octal
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
        consumeNumericSuffix(stream);
    }

    private void consumeNumericSuffix(SourceCharStream stream) {
        char s = stream.peek();
        if (s == 'l' || s == 'L' || s == 'f' || s == 'F' || s == 'd' || s == 'D') {
            stream.consume();
        }
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
