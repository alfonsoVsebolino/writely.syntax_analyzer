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
 * Tokenizer for the C++ programming language adhering to ADR 0004.
 */
public class CppTokenizer implements Tokenizer {

    private static final Set<String> KEYWORDS = Set.of(
        "asm", "auto", "bool", "break", "case", "catch", "char", "class",
        "const", "constexpr", "continue", "default", "delete", "do", "double",
        "else", "enum", "explicit", "export", "extern", "false", "float",
        "for", "friend", "goto", "if", "inline", "int", "long", "mutable",
        "namespace", "new", "noexcept", "nullptr", "operator", "private",
        "protected", "public", "register", "return", "short", "signed",
        "sizeof", "static", "struct", "switch", "template", "this",
        "thread_local", "throw", "true", "try", "typedef", "typeid",
        "typename", "union", "unsigned", "using", "virtual", "void",
        "volatile", "wchar_t", "while"
    );

    private static final List<String> OPERATORS = List.of(
        "->*", "<=>", "<<=", ">>=",
        "::", "->", ".*", "++", "--", "==", "!=", "<=", ">=",
        "&&", "||", "<<", ">>", "+=", "-=", "*=", "/=", "%=",
        "&=", "|=", "^=",
        "=", "+", "-", "*", "/", "%", "<", ">", "!",
        "&", "|", "^", "~", "?"
    );

    @Override
    public List<Token> tokenize(SourcePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        if (payload.language() != Language.CPP) {
            throw new IllegalArgumentException("CppTokenizer only supports C++, but got: " + payload.language());
        }

        String source = payload.sourceText();
        if (source.isEmpty()) {
            return Collections.emptyList();
        }

        SourceCharStream stream = new SourceCharStream(source);
        List<Token> tokens = new ArrayList<>();
        boolean atLineStart = true;

        while (stream.hasMore()) {
            char c = stream.peek();

            // 1. Whitespace
            if (c == ' ' || c == '\t') {
                stream.consume();
                continue;
            }

            if (c == '\r' || c == '\n') {
                if (stream.peek() == '\r') stream.consume();
                if (stream.peek() == '\n') stream.consume();
                atLineStart = true;
                continue;
            }

            // 2. Preprocessor Directives (#include, #define, etc.)
            if (atLineStart && c == '#') {
                SourceLocation start = stream.location();
                while (stream.hasMore()) {
                    // Check for backslash line continuation
                    if (stream.peek() == '\\' && (stream.peek(1) == '\n' || (stream.peek(1) == '\r' && stream.peek(2) == '\n') || stream.peek(1) == '\r')) {
                        stream.consume();
                        if (stream.peek() == '\r') stream.consume();
                        if (stream.peek() == '\n') stream.consume();
                    } else if (stream.peek() == '\n' || stream.peek() == '\r') {
                        break;
                    } else {
                        stream.consume();
                    }
                }
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.COMMENT, lexeme, span, "PREPROCESSOR"));
                continue;
            }

            // 3. Comments
            if (stream.startsWith("//")) {
                SourceLocation start = stream.location();
                stream.advance(2);
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
                stream.advance(2);
                while (stream.hasMore()) {
                    if (stream.startsWith("*/")) {
                        stream.advance(2);
                        break;
                    }
                    stream.consume();
                }
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.COMMENT, lexeme, span, "COMMENT"));
                continue;
            }

            // 4. Raw String Literal R"(...)"
            if (stream.startsWith("R\"")) {
                SourceLocation start = stream.location();
                stream.advance(2);
                StringBuilder delim = new StringBuilder();
                while (stream.hasMore() && stream.peek() != '(' && stream.peek() != '\n' && stream.peek() != '\r') {
                    delim.append(stream.consume());
                }
                if (stream.peek() == '(') {
                    stream.consume();
                    String closing = ")" + delim + "\"";
                    while (stream.hasMore()) {
                        if (stream.startsWith(closing)) {
                            stream.advance(closing.length());
                            break;
                        }
                        stream.consume();
                    }
                }
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.LITERAL_STRING, lexeme, span, "LITERAL_STRING"));
                atLineStart = false;
                continue;
            }

            // 5. Standard String Literal "..."
            if (c == '"') {
                SourceLocation start = stream.location();
                stream.consume();
                while (stream.hasMore() && stream.peek() != '\n' && stream.peek() != '\r') {
                    if (stream.peek() == '"') {
                        stream.consume();
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
                atLineStart = false;
                continue;
            }

            // 6. Character Literal '...'
            if (c == '\'') {
                SourceLocation start = stream.location();
                stream.consume();
                while (stream.hasMore() && stream.peek() != '\n' && stream.peek() != '\r') {
                    if (stream.peek() == '\'') {
                        stream.consume();
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
                atLineStart = false;
                continue;
            }

            // 7. Numeric Literals
            if (Character.isDigit(c) || (c == '.' && Character.isDigit(stream.peek(1)))) {
                SourceLocation start = stream.location();
                scanNumericLiteral(stream);
                SourceSpan span = SourceSpan.of(start, stream.location());
                String lexeme = stream.substring(start.charOffset(), stream.charOffset());
                tokens.add(Token.of(TokenType.LITERAL_NUMBER, lexeme, span, "LITERAL_NUMBER"));
                atLineStart = false;
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
                atLineStart = false;
                continue;
            }

            // 9. Operators (longest match first)
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
                atLineStart = false;
                continue;
            }

            // 10. Delimiters
            if (isDelimiter(c)) {
                SourceLocation start = stream.location();
                char delim = stream.consume();
                SourceSpan span = SourceSpan.of(start, stream.location());
                tokens.add(Token.of(TokenType.DELIMITER, String.valueOf(delim), span, "DELIMITER"));
                atLineStart = false;
                continue;
            }

            // 11. Unknown characters
            SourceLocation start = stream.location();
            char unk = stream.consume();
            SourceSpan span = SourceSpan.of(start, stream.location());
            tokens.add(Token.of(TokenType.UNKNOWN, String.valueOf(unk), span, "UNKNOWN"));
            atLineStart = false;
        }

        return Collections.unmodifiableList(tokens);
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private boolean isDelimiter(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']'
            || c == '{' || c == '}' || c == ';' || c == ','
            || c == '.' || c == ':';
    }

    private void scanNumericLiteral(SourceCharStream stream) {
        if (stream.peek() == '0' && (stream.peek(1) == 'x' || stream.peek(1) == 'X')) {
            stream.advance(2);
            while (stream.hasMore() && (isHexDigit(stream.peek()) || stream.peek() == '\'')) {
                stream.consume();
            }
            consumeNumericSuffix(stream);
            return;
        }

        if (stream.peek() == '0' && (stream.peek(1) == 'b' || stream.peek(1) == 'B')) {
            stream.advance(2);
            while (stream.hasMore() && (stream.peek() == '0' || stream.peek() == '1' || stream.peek() == '\'')) {
                stream.consume();
            }
            consumeNumericSuffix(stream);
            return;
        }

        while (stream.hasMore() && (Character.isDigit(stream.peek()) || stream.peek() == '\'')) {
            stream.consume();
        }
        if (stream.peek() == '.' && stream.peek(1) != '.') {
            stream.consume();
            while (stream.hasMore() && (Character.isDigit(stream.peek()) || stream.peek() == '\'')) {
                stream.consume();
            }
        }
        if (stream.peek() == 'e' || stream.peek() == 'E') {
            stream.consume();
            if (stream.peek() == '+' || stream.peek() == '-') {
                stream.consume();
            }
            while (stream.hasMore() && (Character.isDigit(stream.peek()) || stream.peek() == '\'')) {
                stream.consume();
            }
        }
        consumeNumericSuffix(stream);
    }

    private void consumeNumericSuffix(SourceCharStream stream) {
        while (stream.hasMore()) {
            char s = Character.toLowerCase(stream.peek());
            if (s == 'u' || s == 'l' || s == 'f') {
                stream.consume();
            } else {
                break;
            }
        }
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
