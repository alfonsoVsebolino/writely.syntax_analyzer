package com.writely.syntax_analyzer.core.check;

import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Syntax checker enforcing language-aware identifier naming and placement rules
 * in accordance with Issue #17 and ADR 0008.
 */
public class IdentifierSyntaxChecker implements SyntaxChecker {

    public static final String ERR_IDENTIFIER_STARTS_WITH_DIGIT = "ERR_IDENTIFIER_STARTS_WITH_DIGIT";
    public static final String ERR_ILLEGAL_IDENTIFIER_CHAR = "ERR_ILLEGAL_IDENTIFIER_CHAR";
    public static final String ERR_KEYWORD_AS_IDENTIFIER = "ERR_KEYWORD_AS_IDENTIFIER";

    private static final Set<String> TYPE_KEYWORDS = Set.of(
        "int", "boolean", "bool", "char", "float", "double", "long", "short",
        "byte", "void", "auto", "signed", "unsigned", "wchar_t"
    );

    private static final Set<String> DECLARATION_KEYWORDS = Set.of(
        "class", "interface", "enum", "record", "struct", "namespace"
    );

    private static final Set<String> MODIFIER_KEYWORDS = Set.of(
        "public", "private", "protected", "static", "final", "abstract",
        "synchronized", "volatile", "transient", "native", "strictfp",
        "sealed", "non-sealed",
        "const", "constexpr", "virtual", "inline", "explicit", "friend",
        "mutable", "thread_local", "noexcept", "extern", "register"
    );

    private static final Set<String> PYTHON_DECL_OR_ALIAS_KEYWORDS = Set.of(
        "def", "class", "as"
    );

    private static final Set<String> JAVA_RESERVED_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "_"
    );

    private static final Set<String> PYTHON_RESERVED_KEYWORDS = Set.of(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
        "while", "with", "yield"
    );

    private static final Set<String> CPP_RESERVED_KEYWORDS = Set.of(
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

    public IdentifierSyntaxChecker() {
    }

    @Override
    public CheckCategory category() {
        return CheckCategory.IDENTIFIER_NAMING;
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

        // 1. Identifiers starting with a digit: LITERAL_NUMBER directly abutting IDENTIFIER / KEYWORD
        checkStartsWithDigit(tokens, diagnostics);

        // 2. Language-specific illegal characters in or abutting identifiers
        checkIllegalCharacters(language, tokens, diagnostics);

        // 3. Reserved keywords appearing in unambiguous declaration / identifier positions
        checkKeywordsAsIdentifiers(language, tokens, diagnostics);

        diagnostics.sort(Comparator.comparing(Diagnostic::location));
        return Collections.unmodifiableList(diagnostics);
    }

    private void checkStartsWithDigit(List<Token> tokens, List<Diagnostic> diagnostics) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            Token current = tokens.get(i);
            Token next = tokens.get(i + 1);

            if (current.tokenType() == TokenType.LITERAL_NUMBER
                && isIdentifierOrKeyword(next)
                && current.endLocation().charOffset() == next.startLocation().charOffset()) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.IDENTIFIER_NAMING,
                    current.startLocation(),
                    String.format("Identifier cannot start with a digit: '%s%s'", current.lexeme(), next.lexeme()),
                    ERR_IDENTIFIER_STARTS_WITH_DIGIT,
                    "Prefix identifier with a letter or underscore, or separate number from identifier"
                ));
            }
        }
    }

    private void checkIllegalCharacters(Language language, List<Token> tokens, List<Diagnostic> diagnostics) {
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            // Flag UNKNOWN tokens directly or contiguously abutting an identifier
            if (token.tokenType() == TokenType.UNKNOWN && isAbuttingIdentifier(tokens, i)) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.IDENTIFIER_NAMING,
                    token.startLocation(),
                    String.format("Illegal character '%s' in or abutting identifier", token.lexeme()),
                    ERR_ILLEGAL_IDENTIFIER_CHAR,
                    String.format("Remove illegal character '%s' from identifier", token.lexeme())
                ));
                continue;
            }

            // Flag identifiers containing '$' in Python and C++
            if ((language == Language.PYTHON || language == Language.CPP)
                && token.tokenType() == TokenType.IDENTIFIER
                && token.lexeme().contains("$")) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.IDENTIFIER_NAMING,
                    token.startLocation(),
                    String.format("Illegal character '$' in identifier '%s'", token.lexeme()),
                    ERR_ILLEGAL_IDENTIFIER_CHAR,
                    "Remove '$' from identifier name"
                ));
            }
        }
    }

    private boolean isAbuttingIdentifier(List<Token> tokens, int index) {
        // Look left across contiguous UNKNOWN tokens
        int left = index - 1;
        while (left >= 0 && tokens.get(left).tokenType() == TokenType.UNKNOWN
            && tokens.get(left).endLocation().charOffset() == tokens.get(left + 1).startLocation().charOffset()) {
            left--;
        }
        if (left >= 0 && isIdentifierOrKeyword(tokens.get(left))
            && tokens.get(left).endLocation().charOffset() == tokens.get(left + 1).startLocation().charOffset()) {
            return true;
        }

        // Look right across contiguous UNKNOWN tokens
        int right = index + 1;
        while (right < tokens.size() && tokens.get(right).tokenType() == TokenType.UNKNOWN
            && tokens.get(right - 1).endLocation().charOffset() == tokens.get(right).startLocation().charOffset()) {
            right++;
        }
        if (right < tokens.size() && isIdentifierOrKeyword(tokens.get(right))
            && tokens.get(right - 1).endLocation().charOffset() == tokens.get(right).startLocation().charOffset()) {
            return true;
        }

        return false;
    }

    private void checkKeywordsAsIdentifiers(Language language, List<Token> tokens, List<Diagnostic> diagnostics) {
        List<Token> nonTrivia = new ArrayList<>();
        for (Token t : tokens) {
            if (!t.isTrivia()) {
                nonTrivia.add(t);
            }
        }

        if (nonTrivia.size() < 2) {
            return;
        }

        for (int i = 0; i < nonTrivia.size() - 1; i++) {
            Token current = nonTrivia.get(i);
            Token next = nonTrivia.get(i + 1);

            if (current.tokenType() != TokenType.KEYWORD || next.tokenType() != TokenType.KEYWORD) {
                continue;
            }

            String curLexeme = current.lexeme();
            String nextLexeme = next.lexeme();

            if (language == Language.PYTHON) {
                if (PYTHON_DECL_OR_ALIAS_KEYWORDS.contains(curLexeme) && isReservedKeyword(language, nextLexeme)) {
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.IDENTIFIER_NAMING,
                        next.startLocation(),
                        String.format("Reserved keyword '%s' cannot be used as an identifier", nextLexeme),
                        ERR_KEYWORD_AS_IDENTIFIER,
                        String.format("Replace '%s' with a valid identifier name", nextLexeme)
                    ));
                }
            } else {
                // Java and C++
                if (DECLARATION_KEYWORDS.contains(curLexeme)) {
                    // In C++, 'enum class' and 'enum struct' are valid scoped enumeration headers
                    boolean isCppScopedEnum = language == Language.CPP
                        && "enum".equals(curLexeme)
                        && ("class".equals(nextLexeme) || "struct".equals(nextLexeme));

                    if (!isCppScopedEnum && isReservedKeyword(language, nextLexeme)) {
                        diagnostics.add(Diagnostic.error(
                            CheckCategory.IDENTIFIER_NAMING,
                            next.startLocation(),
                            String.format("Reserved keyword '%s' cannot be used as an identifier", nextLexeme),
                            ERR_KEYWORD_AS_IDENTIFIER,
                            String.format("Replace '%s' with a valid identifier name", nextLexeme)
                        ));
                    }
                } else if (TYPE_KEYWORDS.contains(curLexeme)) {
                    boolean isAllowedTypeContinuation = language == Language.CPP
                        && isCppTypeCombination(curLexeme, nextLexeme);

                    if (!isAllowedTypeContinuation && isReservedKeyword(language, nextLexeme)) {
                        diagnostics.add(Diagnostic.error(
                            CheckCategory.IDENTIFIER_NAMING,
                            next.startLocation(),
                            String.format("Reserved keyword '%s' cannot be used as an identifier", nextLexeme),
                            ERR_KEYWORD_AS_IDENTIFIER,
                            String.format("Replace '%s' with a valid identifier name", nextLexeme)
                        ));
                    }
                } else if (MODIFIER_KEYWORDS.contains(curLexeme)) {
                    boolean isAllowedModifierFollower = MODIFIER_KEYWORDS.contains(nextLexeme)
                        || DECLARATION_KEYWORDS.contains(nextLexeme)
                        || TYPE_KEYWORDS.contains(nextLexeme);

                    if (!isAllowedModifierFollower && isReservedKeyword(language, nextLexeme)) {
                        diagnostics.add(Diagnostic.error(
                            CheckCategory.IDENTIFIER_NAMING,
                            next.startLocation(),
                            String.format("Reserved keyword '%s' cannot be used as an identifier", nextLexeme),
                            ERR_KEYWORD_AS_IDENTIFIER,
                            String.format("Replace '%s' with a valid identifier name", nextLexeme)
                        ));
                    }
                }
            }
        }
    }

    private static boolean isReservedKeyword(Language language, String word) {
        return switch (language) {
            case JAVA -> JAVA_RESERVED_KEYWORDS.contains(word);
            case PYTHON -> PYTHON_RESERVED_KEYWORDS.contains(word);
            case CPP -> CPP_RESERVED_KEYWORDS.contains(word);
        };
    }

    private static boolean isIdentifierOrKeyword(Token token) {
        TokenType type = token.tokenType();
        return type == TokenType.IDENTIFIER || type == TokenType.KEYWORD;
    }

    private static boolean isCppTypeCombination(String first, String second) {
        if ("long".equals(first) && ("long".equals(second) || "double".equals(second) || "int".equals(second))) {
            return true;
        }
        if ("short".equals(first) && "int".equals(second)) {
            return true;
        }
        if (("signed".equals(first) || "unsigned".equals(first))
            && ("int".equals(second) || "char".equals(second) || "short".equals(second) || "long".equals(second))) {
            return true;
        }
        return false;
    }
}
