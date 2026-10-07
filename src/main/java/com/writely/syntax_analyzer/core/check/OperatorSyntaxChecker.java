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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Syntax checker enforcing language-aware operator syntax validation across Java, Python, and C++
 * in accordance with Issue #22 and ADR 0013.
 */
public class OperatorSyntaxChecker implements SyntaxChecker {

    public static final String ERR_INVALID_OPERATOR_SEQUENCE = "ERR_INVALID_OPERATOR_SEQUENCE";
    public static final String ERR_MISSING_OPERAND = "ERR_MISSING_OPERAND";
    public static final String ERR_INVALID_UNARY_OPERATOR = "ERR_INVALID_UNARY_OPERATOR";

    private static final Set<String> JAVA_PREFIX_UNARY = Set.of(
        "+", "-", "!", "~", "++", "--"
    );

    private static final Set<String> CPP_PREFIX_UNARY = Set.of(
        "+", "-", "!", "~", "++", "--", "*", "&", "::"
    );

    private static final Set<String> PYTHON_PREFIX_UNARY = Set.of(
        "+", "-", "~", "not", "*", "**"
    );

    private static final Set<String> PYTHON_KEYWORD_OPERATORS = Set.of(
        "and", "or", "not", "is", "in"
    );

    private static final Set<String> DECLARATION_OR_TYPE_KEYWORDS = Set.of(
        "class", "interface", "record", "enum", "struct", "template",
        "public", "private", "protected", "static", "final", "abstract",
        "int", "boolean", "bool", "char", "float", "double", "long", "short",
        "byte", "void", "auto", "var", "const", "new"
    );

    private static final Set<String> STATEMENT_EXPR_KEYWORDS = Set.of(
        "return", "throw", "yield", "assert", "case", "default",
        "if", "elif", "while", "for", "with", "lambda"
    );

    public OperatorSyntaxChecker() {
    }

    @Override
    public CheckCategory category() {
        return CheckCategory.OPERATOR_SYNTAX;
    }

    @Override
    public List<Diagnostic> check(SourcePayload payload, List<Token> tokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");

        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        Language language = payload.language();
        List<Token> filtered = filterTokens(language, tokens);
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        checkTokens(language, filtered, diagnostics);

        diagnostics.sort(Comparator.comparing(Diagnostic::location));
        return Collections.unmodifiableList(diagnostics);
    }

    private List<Token> filterTokens(Language language, List<Token> tokens) {
        List<Token> result = new ArrayList<>(tokens.size());
        for (Token t : tokens) {
            if (language == Language.PYTHON) {
                // Keep NEWLINE tokens in Python for statement boundary detection, skip comments & whitespace
                if (t.tokenType() != TokenType.COMMENT && t.tokenType() != TokenType.WHITESPACE) {
                    result.add(t);
                }
            } else {
                if (!t.isTrivia()) {
                    result.add(t);
                }
            }
        }
        return result;
    }

    private void checkTokens(Language language, List<Token> tokens, List<Diagnostic> diagnostics) {
        int n = tokens.size();
        int bracketDepth = 0;

        for (int i = 0; i < n; i++) {
            Token curr = tokens.get(i);

            // Track bracket depth in Python for newline isolation
            if (language == Language.PYTHON) {
                if (curr.tokenType() == TokenType.DELIMITER) {
                    if ("(".equals(curr.lexeme()) || "[".equals(curr.lexeme()) || "{".equals(curr.lexeme())) {
                        bracketDepth++;
                    } else if (")".equals(curr.lexeme()) || "]".equals(curr.lexeme()) || "}".equals(curr.lexeme())) {
                        if (bracketDepth > 0) {
                            bracketDepth--;
                        }
                    }
                }
                if (curr.tokenType() == TokenType.NEWLINE) {
                    continue;
                }
                if (curr.tokenType() == TokenType.INDENT || curr.tokenType() == TokenType.DEDENT) {
                    continue;
                }
            }

            if (!isOperator(language, curr)) {
                continue;
            }

            Token prev = findPreviousToken(language, tokens, i);
            Token next = findNextToken(language, tokens, i, bracketDepth);

            boolean isPostfix = isPostfixUsage(prev, curr);

            // 1. Literal increment/decrement validation: 5++, ++5, and chained ++
            if ("++".equals(curr.lexeme()) || "--".equals(curr.lexeme())) {
                if (prev != null && prev.tokenType().isLiteral()) {
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.OPERATOR_SYNTAX,
                        curr.startLocation(),
                        String.format("Invalid unary operator application: cannot apply '%s' to a literal", curr.lexeme()),
                        ERR_INVALID_UNARY_OPERATOR,
                        String.format("Apply '%s' to a variable instead of a literal", curr.lexeme())
                    ));
                    continue;
                }

                if (!isPostfix && next != null && next.tokenType().isLiteral()) {
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.OPERATOR_SYNTAX,
                        curr.startLocation(),
                        String.format("Invalid unary operator application: cannot apply '%s' to a literal", curr.lexeme()),
                        ERR_INVALID_UNARY_OPERATOR,
                        String.format("Apply '%s' to a variable instead of a literal", curr.lexeme())
                    ));
                    continue;
                }

                if (prev != null && ("++".equals(prev.lexeme()) || "--".equals(prev.lexeme()))) {
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.OPERATOR_SYNTAX,
                        curr.startLocation(),
                        "Invalid unary operator application: cannot chain increment or decrement operators",
                        ERR_INVALID_UNARY_OPERATOR,
                        String.format("Remove redundant operator '%s'", curr.lexeme())
                    ));
                    continue;
                }
            }

            // 2. Missing right operand / terminator checks (e.g. x + ;, 5 *)
            if (isExpressionEnd(language, next, bracketDepth)) {
                if (isPostfix) {
                    // Valid postfix operator (e.g. i++; or i++) or i++,)
                    continue;
                }

                if (isPureUnaryOperator(language, curr.lexeme())) {
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.OPERATOR_SYNTAX,
                        curr.startLocation(),
                        String.format("Invalid unary operator application: missing operand for '%s'", curr.lexeme()),
                        ERR_INVALID_UNARY_OPERATOR,
                        String.format("Provide an operand for unary operator '%s'", curr.lexeme())
                    ));
                } else {
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.OPERATOR_SYNTAX,
                        curr.startLocation(),
                        String.format("Missing right operand for binary operator '%s'", curr.lexeme()),
                        ERR_MISSING_OPERAND,
                        String.format("Provide right operand for operator '%s'", curr.lexeme())
                    ));
                }
                continue;
            }

            // 3. Consecutive operators check: curr followed by next
            if (next != null && isOperator(language, next)) {
                // If curr was postfix, next is a binary operator or following expression
                if (isPostfix) {
                    continue;
                }

                // Check if this consecutive pair is valid in the language
                if (isValidConsecutivePair(language, prev, curr, next)) {
                    continue;
                }

                // Invalid sequence where next cannot be a unary prefix operator
                diagnostics.add(Diagnostic.error(
                    CheckCategory.OPERATOR_SYNTAX,
                    next.startLocation(),
                    String.format("Invalid consecutive operator sequence '%s %s'", curr.lexeme(), next.lexeme()),
                    ERR_INVALID_OPERATOR_SEQUENCE,
                    String.format("Remove unexpected operator '%s'", next.lexeme())
                ));
                continue;
            }

            // 4. Missing left operand check (strictly binary operator at expression start)
            if (isExpressionStart(language, prev)) {
                if (isValidPrefixOperator(language, prev, curr)) {
                    continue;
                }

                diagnostics.add(Diagnostic.error(
                    CheckCategory.OPERATOR_SYNTAX,
                    curr.startLocation(),
                    String.format("Missing left operand for binary operator '%s'", curr.lexeme()),
                    ERR_MISSING_OPERAND,
                    String.format("Provide left operand for operator '%s'", curr.lexeme())
                ));
            }
        }
    }

    private boolean isOperator(Language language, Token tok) {
        if (tok.tokenType() == TokenType.OPERATOR) {
            return true;
        }
        if (language == Language.PYTHON && tok.tokenType() == TokenType.KEYWORD) {
            return PYTHON_KEYWORD_OPERATORS.contains(tok.lexeme());
        }
        return false;
    }

    private boolean isPostfixUsage(Token prev, Token curr) {
        if (!"++".equals(curr.lexeme()) && !"--".equals(curr.lexeme())) {
            return false;
        }
        if (prev == null) {
            return false;
        }
        if (prev.tokenType() == TokenType.IDENTIFIER) {
            return true;
        }
        return ")".equals(prev.lexeme()) || "]".equals(prev.lexeme());
    }

    private boolean isPrefixPosition(Language language, Token prev) {
        if (prev == null) {
            return true;
        }
        return isExpressionStart(language, prev);
    }

    private boolean isPureUnaryOperator(Language language, String lexeme) {
        if ("!".equals(lexeme) || "~".equals(lexeme) || "++".equals(lexeme) || "--".equals(lexeme)) {
            return true;
        }
        if (language == Language.PYTHON && "not".equals(lexeme)) {
            return true;
        }
        return false;
    }

    private boolean isExpressionEnd(Language language, Token next, int bracketDepth) {
        if (next == null) {
            return true;
        }
        String lex = next.lexeme();
        if (";".equals(lex) || ")".equals(lex) || "]".equals(lex) || "}".equals(lex) || ",".equals(lex)) {
            return true;
        }
        if (language == Language.PYTHON) {
            if (bracketDepth == 0 && next.tokenType() == TokenType.NEWLINE) {
                return true;
            }
            if (next.tokenType() == TokenType.DEDENT) {
                return true;
            }
        }
        return false;
    }

    private boolean isExpressionStart(Language language, Token prev) {
        if (prev == null) {
            return true;
        }
        String lex = prev.lexeme();
        if (";".equals(lex) || "{".equals(lex) || "}".equals(lex)
            || "(".equals(lex) || "[".equals(lex) || ",".equals(lex)
            || ":".equals(lex) || "?".equals(lex) || "->".equals(lex)) {
            return true;
        }
        if (language == Language.PYTHON) {
            if (prev.tokenType() == TokenType.NEWLINE
                || prev.tokenType() == TokenType.INDENT
                || prev.tokenType() == TokenType.DEDENT) {
                return true;
            }
        }
        if (prev.tokenType() == TokenType.KEYWORD) {
            return STATEMENT_EXPR_KEYWORDS.contains(prev.lexeme());
        }
        return false;
    }

    private boolean isValidPrefixOperator(Language language, Token prev, Token curr) {
        String lex = curr.lexeme();
        switch (language) {
            case JAVA -> {
                if (JAVA_PREFIX_UNARY.contains(lex)) {
                    return true;
                }
                // Generic type/method opening <T>
                if ("<".equals(lex) && prev != null && (prev.tokenType() == TokenType.IDENTIFIER || DECLARATION_OR_TYPE_KEYWORDS.contains(prev.lexeme()))) {
                    return true;
                }
                return false;
            }
            case CPP -> {
                if (CPP_PREFIX_UNARY.contains(lex)) {
                    return true;
                }
                // Template parameter or argument opening <T>
                if ("<".equals(lex) && prev != null && (prev.tokenType() == TokenType.IDENTIFIER || DECLARATION_OR_TYPE_KEYWORDS.contains(prev.lexeme()))) {
                    return true;
                }
                return false;
            }
            case PYTHON -> {
                return PYTHON_PREFIX_UNARY.contains(lex);
            }
        }
        return false;
    }

    private boolean isValidConsecutivePair(Language language, Token prev, Token curr, Token next) {
        String curLex = curr.lexeme();
        String nextLex = next.lexeme();

        // 1. Java Diamond operator: <>
        if (language == Language.JAVA && "<".equals(curLex) && ">".equals(nextLex)) {
            return true;
        }

        // 2. C++ Template closing pointer / reference: <Type*> or <Type&>
        if (language == Language.CPP && ("*".equals(curLex) || "&".equals(curLex)) && ">".equals(nextLex)) {
            return true;
        }

        // 3. C++ pointer/reference combinations: int** ptr, *&ref, &*ptr
        if (language == Language.CPP && ("*".equals(curLex) || "&".equals(curLex)) && ("*".equals(nextLex) || "&".equals(nextLex))) {
            return true;
        }

        // 4. C++ Global scope resolution: ::globalVar
        if (language == Language.CPP && "::".equals(nextLex)) {
            return true;
        }

        // 5. Python valid multi-keyword comparisons and logical combinations
        if (language == Language.PYTHON) {
            if ("is".equals(curLex) && "not".equals(nextLex)) {
                return true;
            }
            if ("not".equals(curLex) && "in".equals(nextLex)) {
                return true;
            }
            if ("and".equals(curLex) && "not".equals(nextLex)) {
                return true;
            }
            if ("or".equals(curLex) && "not".equals(nextLex)) {
                return true;
            }
            if ("not".equals(curLex) && "not".equals(nextLex)) {
                return true;
            }
        }

        // 6. Valid unary operator following any operator (e.g. x + +y, x - -y, x = -1, x == !flag, x && !flag, x + *p in C++)
        switch (language) {
            case JAVA -> {
                if (JAVA_PREFIX_UNARY.contains(nextLex)) {
                    return true;
                }
            }
            case CPP -> {
                if (CPP_PREFIX_UNARY.contains(nextLex)) {
                    return true;
                }
            }
            case PYTHON -> {
                if (PYTHON_PREFIX_UNARY.contains(nextLex)) {
                    // In Python, * and ** cannot follow binary operators (e.g. + *, / *, == *)
                    if ("*".equals(nextLex) || "**".equals(nextLex)) {
                        return "=".equals(curLex);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private Token findPreviousToken(Language language, List<Token> tokens, int currentIndex) {
        for (int i = currentIndex - 1; i >= 0; i--) {
            Token t = tokens.get(i);
            if (language == Language.PYTHON && t.tokenType() == TokenType.NEWLINE) {
                return t;
            }
            if (!t.isTrivia()) {
                return t;
            }
        }
        return null;
    }

    private Token findNextToken(Language language, List<Token> tokens, int currentIndex, int bracketDepth) {
        for (int i = currentIndex + 1; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (language == Language.PYTHON) {
                if (t.tokenType() == TokenType.NEWLINE) {
                    if (bracketDepth == 0) {
                        return t;
                    }
                    continue;
                }
                if (t.tokenType() == TokenType.INDENT || t.tokenType() == TokenType.DEDENT) {
                    return t;
                }
            }
            if (!t.isTrivia()) {
                return t;
            }
        }
        return null;
    }
}
