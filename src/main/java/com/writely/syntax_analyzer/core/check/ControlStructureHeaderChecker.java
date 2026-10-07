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
 * Syntax checker enforcing language-aware control-structure header validation
 * across Java, C++, and Python in accordance with Issue #23 and ADR 0014.
 */
public class ControlStructureHeaderChecker implements SyntaxChecker {

    public static final String ERR_MALFORMED_CONTROL_HEADER = "ERR_MALFORMED_CONTROL_HEADER";
    public static final String ERR_MISSING_HEADER_DELIMITER = "ERR_MISSING_HEADER_DELIMITER";
    public static final String ERR_MISSING_HEADER_COLON = "ERR_MISSING_HEADER_COLON";
    public static final String ERR_MALFORMED_FOR_HEADER = "ERR_MALFORMED_FOR_HEADER";

    private static final Set<String> JAVA_CPP_CONTROL_KEYWORDS = Set.of(
        "if", "while", "switch", "for"
    );

    private static final Set<String> PYTHON_CONTROL_KEYWORDS = Set.of(
        "if", "elif", "else", "while", "for", "try", "except", "finally", "with", "match", "case"
    );

    public ControlStructureHeaderChecker() {
    }

    @Override
    public CheckCategory category() {
        return CheckCategory.CONTROL_HEADER;
    }

    @Override
    public List<Diagnostic> check(SourcePayload payload, List<Token> tokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");

        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<Diagnostic> diagnostics = switch (payload.language()) {
            case JAVA, CPP -> checkJavaCpp(payload.language(), tokens);
            case PYTHON -> checkPython(tokens);
        };

        List<Diagnostic> sorted = new ArrayList<>(diagnostics);
        sorted.sort(Comparator.comparing(Diagnostic::location));
        return Collections.unmodifiableList(sorted);
    }

    // =========================================================================
    // Java and C++ Control Structure Header Validation
    // =========================================================================

    private List<Diagnostic> checkJavaCpp(Language language, List<Token> rawTokens) {
        List<Token> tokens = filterTriviaTokens(rawTokens);
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            Token tok = tokens.get(i);
            if (tok.tokenType() != TokenType.KEYWORD || !JAVA_CPP_CONTROL_KEYWORDS.contains(tok.lexeme())) {
                i++;
                continue;
            }

            // Exclude member access like obj.if, obj.while, Type::switch
            if (i > 0) {
                String prevLexeme = tokens.get(i - 1).lexeme();
                if (".".equals(prevLexeme) || "->".equals(prevLexeme) || "::".equals(prevLexeme)) {
                    i++;
                    continue;
                }
            }

            String kw = tok.lexeme();
            if ("for".equals(kw)) {
                i = checkJavaCppFor(tokens, i, diagnostics);
            } else {
                i = checkJavaCppConditionHeader(language, tokens, i, diagnostics);
            }
        }

        return diagnostics;
    }

    private int checkJavaCppConditionHeader(Language language, List<Token> tokens, int kwIndex, List<Diagnostic> diagnostics) {
        Token kwTok = tokens.get(kwIndex);
        int nextIndex = kwIndex + 1;

        // In C++, "if constexpr" is allowed
        if (language == Language.CPP && "if".equals(kwTok.lexeme()) && nextIndex < tokens.size()
                && "constexpr".equals(tokens.get(nextIndex).lexeme())) {
            nextIndex++;
        }

        if (nextIndex >= tokens.size()) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                kwTok.endLocation(),
                "Expected '(' after '" + kwTok.lexeme() + "'",
                ERR_MISSING_HEADER_DELIMITER,
                "Insert '('"
            ));
            return nextIndex;
        }

        Token nextTok = tokens.get(nextIndex);
        if (!"(".equals(nextTok.lexeme())) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                nextTok.startLocation(),
                "Expected '(' after '" + kwTok.lexeme() + "'",
                ERR_MISSING_HEADER_DELIMITER,
                "Insert '('"
            ));

            // Recovery: scan forward until matching closing ')' or '{' or ';' or EOF
            int j = nextIndex;
            while (j < tokens.size()) {
                Token t = tokens.get(j);
                if (")".equals(t.lexeme())) {
                    return j + 1;
                }
                if ("{".equals(t.lexeme()) || ";".equals(t.lexeme())) {
                    return j;
                }
                j++;
            }
            return j;
        }

        Token openParen = nextTok;
        int parenDepth = 1;
        int braceDepth = 0;
        int bracketDepth = 0;
        List<Token> condTokens = new ArrayList<>();
        int j = nextIndex + 1;
        boolean foundClosingParen = false;

        while (j < tokens.size()) {
            Token t = tokens.get(j);
            if ("(".equals(t.lexeme())) {
                parenDepth++;
            } else if (")".equals(t.lexeme())) {
                parenDepth--;
                if (parenDepth == 0) {
                    foundClosingParen = true;
                    break;
                }
            } else if ("{".equals(t.lexeme())) {
                if (parenDepth == 1 && braceDepth == 0 && (j == nextIndex + 1 || !"->".equals(tokens.get(j - 1).lexeme()))) {
                    break;
                }
                braceDepth++;
            } else if ("}".equals(t.lexeme())) {
                if (braceDepth > 0) braceDepth--;
            } else if ("[".equals(t.lexeme())) {
                bracketDepth++;
            } else if ("]".equals(t.lexeme())) {
                if (bracketDepth > 0) bracketDepth--;
            } else if (";".equals(t.lexeme()) && parenDepth == 1 && braceDepth == 0 && bracketDepth == 0) {
                if (!hasClosingParenLookahead(tokens, j + 1)) {
                    break;
                }
            }

            condTokens.add(t);
            j++;
        }

        if (foundClosingParen) {
            Token closeParen = tokens.get(j);
            if (condTokens.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.CONTROL_HEADER,
                    closeParen.startLocation(),
                    "switch".equals(kwTok.lexeme())
                        ? "Missing selector expression in 'switch' header"
                        : "Missing condition expression in '" + kwTok.lexeme() + "' header",
                    ERR_MALFORMED_CONTROL_HEADER,
                    "switch".equals(kwTok.lexeme()) ? "Specify selector expression" : "Specify condition expression"
                ));
            }
            return j + 1;
        } else {
            Token stopTok = j < tokens.size() ? tokens.get(j) : tokens.get(tokens.size() - 1);
            diagnostics.add(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                stopTok.startLocation(),
                "Missing closing ')' in '" + kwTok.lexeme() + "' header",
                ERR_MISSING_HEADER_DELIMITER,
                "Insert ')'"
            ));
            return j;
        }
    }

    private boolean hasClosingParenLookahead(List<Token> tokens, int fromIndex) {
        int depth = 1;
        int brDepth = 0;
        for (int k = fromIndex; k < tokens.size(); k++) {
            Token t = tokens.get(k);
            if ("(".equals(t.lexeme())) {
                depth++;
            } else if (")".equals(t.lexeme())) {
                depth--;
                if (depth == 0) {
                    return true;
                }
            } else if ("{".equals(t.lexeme()) && depth == 1 && brDepth == 0) {
                return false;
            } else if ("{".equals(t.lexeme())) {
                brDepth++;
            } else if ("}".equals(t.lexeme()) && brDepth > 0) {
                brDepth--;
            }
        }
        return false;
    }

    private int checkJavaCppFor(List<Token> tokens, int kwIndex, List<Diagnostic> diagnostics) {
        Token kwTok = tokens.get(kwIndex);
        int nextIndex = kwIndex + 1;

        if (nextIndex >= tokens.size()) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                kwTok.endLocation(),
                "Expected '(' after 'for'",
                ERR_MISSING_HEADER_DELIMITER,
                "Insert '('"
            ));
            return nextIndex;
        }

        Token nextTok = tokens.get(nextIndex);
        if (!"(".equals(nextTok.lexeme())) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                nextTok.startLocation(),
                "Expected '(' after 'for'",
                ERR_MISSING_HEADER_DELIMITER,
                "Insert '('"
            ));

            int j = nextIndex;
            while (j < tokens.size()) {
                Token t = tokens.get(j);
                if ("{".equals(t.lexeme()) || ";".equals(t.lexeme())) {
                    return j;
                }
                j++;
            }
            return j;
        }

        Token openParen = nextTok;
        int parenDepth = 1;
        int braceDepth = 0;
        int bracketDepth = 0;
        List<Token> insideTokens = new ArrayList<>();
        int j = nextIndex + 1;
        boolean foundClosingParen = false;

        while (j < tokens.size()) {
            Token t = tokens.get(j);
            if ("(".equals(t.lexeme())) {
                parenDepth++;
            } else if (")".equals(t.lexeme())) {
                parenDepth--;
                if (parenDepth == 0) {
                    foundClosingParen = true;
                    break;
                }
            } else if ("{".equals(t.lexeme())) {
                if (parenDepth == 1 && braceDepth == 0 && (j == nextIndex + 1 || !"->".equals(tokens.get(j - 1).lexeme()))) {
                    break;
                }
                braceDepth++;
            } else if ("}".equals(t.lexeme())) {
                if (braceDepth > 0) braceDepth--;
            } else if ("[".equals(t.lexeme())) {
                bracketDepth++;
            } else if ("]".equals(t.lexeme())) {
                if (bracketDepth > 0) bracketDepth--;
            }

            insideTokens.add(t);
            j++;
        }

        if (!foundClosingParen) {
            Token stopTok = j < tokens.size() ? tokens.get(j) : tokens.get(tokens.size() - 1);
            diagnostics.add(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                stopTok.startLocation(),
                "Missing closing ')' in 'for' header",
                ERR_MISSING_HEADER_DELIMITER,
                "Insert ')'"
            ));
            return j;
        }

        validateJavaCppForClauses(insideTokens, openParen, diagnostics);
        return j + 1;
    }

    private void validateJavaCppForClauses(List<Token> insideTokens, Token openParen, List<Diagnostic> diagnostics) {
        int pDepth = 0;
        int bDepth = 0;
        int brDepth = 0;
        List<Token> topLevelSemicolons = new ArrayList<>();
        Token topLevelColon = null;
        int topLevelColonIndex = -1;
        boolean seenQuestion = false;

        for (int k = 0; k < insideTokens.size(); k++) {
            Token t = insideTokens.get(k);
            if ("(".equals(t.lexeme())) pDepth++;
            else if (")".equals(t.lexeme())) pDepth--;
            else if ("[".equals(t.lexeme())) bDepth++;
            else if ("]".equals(t.lexeme())) bDepth--;
            else if ("{".equals(t.lexeme())) brDepth++;
            else if ("}".equals(t.lexeme())) brDepth--;
            else if (pDepth == 0 && bDepth == 0 && brDepth == 0) {
                if (";".equals(t.lexeme())) {
                    topLevelSemicolons.add(t);
                } else if ("?".equals(t.lexeme())) {
                    seenQuestion = true;
                } else if (":".equals(t.lexeme()) && !seenQuestion && topLevelColon == null) {
                    topLevelColon = t;
                    topLevelColonIndex = k;
                }
            }
        }

        // 1. Enhanced / range-based for
        if (topLevelColon != null && topLevelSemicolons.isEmpty()) {
            List<Token> target = insideTokens.subList(0, topLevelColonIndex);
            List<Token> iterable = insideTokens.subList(topLevelColonIndex + 1, insideTokens.size());
            if (target.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.CONTROL_HEADER,
                    topLevelColon.startLocation(),
                    "Missing variable declaration in enhanced 'for' loop header",
                    ERR_MALFORMED_FOR_HEADER,
                    "Specify loop variable"
                ));
            } else if (iterable.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.CONTROL_HEADER,
                    topLevelColon.startLocation(),
                    "Missing iterable expression in enhanced 'for' loop header",
                    ERR_MALFORMED_FOR_HEADER,
                    "Specify iterable expression"
                ));
            }
            return;
        }

        // 2. Traditional 3-part for loop
        int count = topLevelSemicolons.size();
        if (count == 2) {
            return;
        }

        SourceLocation errLoc;
        String message;
        if (count == 0) {
            errLoc = openParen.endLocation();
            message = "Malformed 'for' loop header: missing semicolons (expected 2 semicolons)";
        } else if (count == 1) {
            errLoc = topLevelSemicolons.get(0).startLocation();
            message = "Malformed 'for' loop header: expected 2 semicolons, found 1";
        } else {
            errLoc = topLevelSemicolons.get(2).startLocation();
            message = "Malformed 'for' loop header: too many semicolons (expected 2, found " + count + ")";
        }

        diagnostics.add(Diagnostic.error(
            CheckCategory.CONTROL_HEADER,
            errLoc,
            message,
            ERR_MALFORMED_FOR_HEADER,
            "Structure loop header as 'for (init; condition; update)'"
        ));
    }

    // =========================================================================
    // Python Control Structure Header Validation
    // =========================================================================

    private List<Diagnostic> checkPython(List<Token> rawTokens) {
        List<Token> tokens = filterPythonTokens(rawTokens);
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        boolean atStatementStart = true;
        int i = 0;

        while (i < tokens.size()) {
            Token tok = tokens.get(i);

            if (tok.tokenType() == TokenType.NEWLINE || tok.tokenType() == TokenType.INDENT || tok.tokenType() == TokenType.DEDENT) {
                atStatementStart = true;
                i++;
                continue;
            }

            Token keywordTok = null;
            if (atStatementStart) {
                if ("async".equals(tok.lexeme()) && i + 1 < tokens.size()) {
                    Token next = tokens.get(i + 1);
                    if (next.tokenType() == TokenType.KEYWORD && PYTHON_CONTROL_KEYWORDS.contains(next.lexeme())) {
                        keywordTok = next;
                        i++;
                    }
                } else if (tok.tokenType() == TokenType.KEYWORD && PYTHON_CONTROL_KEYWORDS.contains(tok.lexeme())) {
                    keywordTok = tok;
                }
            }

            if (keywordTok == null) {
                atStatementStart = ";".equals(tok.lexeme());
                i++;
                continue;
            }

            int j = i + 1;
            int parenDepth = 0;
            int bracketDepth = 0;
            int braceDepth = 0;
            Token colonTok = null;
            List<Token> clauseTokens = new ArrayList<>();

            while (j < tokens.size()) {
                Token t = tokens.get(j);
                if (t.tokenType() == TokenType.DELIMITER) {
                    if ("(".equals(t.lexeme())) parenDepth++;
                    else if (")".equals(t.lexeme()) && parenDepth > 0) parenDepth--;
                    else if ("[".equals(t.lexeme())) bracketDepth++;
                    else if ("]".equals(t.lexeme()) && bracketDepth > 0) bracketDepth--;
                    else if ("{".equals(t.lexeme())) braceDepth++;
                    else if ("}".equals(t.lexeme()) && braceDepth > 0) braceDepth--;
                    else if (":".equals(t.lexeme()) && parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        colonTok = t;
                        j++;
                        break;
                    }
                }
                if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                    if (t.tokenType() == TokenType.NEWLINE || t.tokenType() == TokenType.INDENT || t.tokenType() == TokenType.DEDENT) {
                        break;
                    }
                }
                clauseTokens.add(t);
                j++;
            }

            validatePythonHeader(keywordTok, colonTok, clauseTokens, diagnostics);

            i = j;
            atStatementStart = false;
        }

        return diagnostics;
    }

    private void validatePythonHeader(Token kwTok, Token colonTok, List<Token> clauseTokens, List<Diagnostic> diagnostics) {
        String kw = kwTok.lexeme();

        // 1. Missing colon:
        if (colonTok == null) {
            if ("for".equals(kw)) {
                boolean hasIn = clauseTokens.stream().anyMatch(t -> "in".equals(t.lexeme()));
                if (!hasIn) {
                    SourceLocation inLoc = clauseTokens.isEmpty() ? kwTok.endLocation() : clauseTokens.get(clauseTokens.size() - 1).endLocation();
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.CONTROL_HEADER,
                        inLoc,
                        "Missing 'in' keyword in 'for' loop header",
                        ERR_MALFORMED_FOR_HEADER,
                        "Add 'in' keyword"
                    ));
                }
            }

            SourceLocation colonLoc = clauseTokens.isEmpty() ? kwTok.endLocation() : clauseTokens.get(clauseTokens.size() - 1).endLocation();
            diagnostics.add(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                colonLoc,
                "Missing ':' at end of '" + kw + "' header",
                ERR_MISSING_HEADER_COLON,
                "Insert ':'"
            ));
            return;
        }

        // 2. Colon is present:
        if ("for".equals(kw)) {
            validatePythonForHeader(kwTok, colonTok, clauseTokens, diagnostics);
            return;
        }

        if ("if".equals(kw) || "elif".equals(kw) || "while".equals(kw)) {
            if (clauseTokens.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.CONTROL_HEADER,
                    colonTok.startLocation(),
                    "Missing condition expression in '" + kw + "' header",
                    ERR_MALFORMED_CONTROL_HEADER,
                    "Specify condition expression"
                ));
            }
            return;
        }

        if ("match".equals(kw)) {
            if (clauseTokens.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.CONTROL_HEADER,
                    colonTok.startLocation(),
                    "Missing subject expression in 'match' header",
                    ERR_MALFORMED_CONTROL_HEADER,
                    "Specify subject expression"
                ));
            }
            return;
        }

        if ("case".equals(kw)) {
            if (clauseTokens.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.CONTROL_HEADER,
                    colonTok.startLocation(),
                    "Missing pattern in 'case' header",
                    ERR_MALFORMED_CONTROL_HEADER,
                    "Specify case pattern"
                ));
            }
            return;
        }

        if ("with".equals(kw)) {
            if (clauseTokens.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.CONTROL_HEADER,
                    colonTok.startLocation(),
                    "Missing context expression in 'with' header",
                    ERR_MALFORMED_CONTROL_HEADER,
                    "Specify context expression"
                ));
            }
            return;
        }

        if ("else".equals(kw) || "try".equals(kw) || "finally".equals(kw)) {
            if (!clauseTokens.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.CONTROL_HEADER,
                    clauseTokens.get(0).startLocation(),
                    "'" + kw + "' header takes no expression",
                    ERR_MALFORMED_CONTROL_HEADER,
                    "Remove expression from header"
                ));
            }
        }
    }

    private void validatePythonForHeader(Token kwTok, Token colonTok, List<Token> clauseTokens, List<Diagnostic> diagnostics) {
        int inIndex = -1;
        int pDepth = 0;
        int bDepth = 0;
        int brDepth = 0;

        for (int k = 0; k < clauseTokens.size(); k++) {
            Token t = clauseTokens.get(k);
            if ("(".equals(t.lexeme())) pDepth++;
            else if (")".equals(t.lexeme())) pDepth--;
            else if ("[".equals(t.lexeme())) bDepth++;
            else if ("]".equals(t.lexeme())) bDepth--;
            else if ("{".equals(t.lexeme())) brDepth++;
            else if ("}".equals(t.lexeme())) brDepth--;
            else if (pDepth == 0 && bDepth == 0 && brDepth == 0 && "in".equals(t.lexeme())) {
                inIndex = k;
                break;
            }
        }

        if (inIndex == -1) {
            SourceLocation loc = clauseTokens.isEmpty() ? kwTok.endLocation() : clauseTokens.get(clauseTokens.size() - 1).endLocation();
            diagnostics.add(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                loc,
                "Missing 'in' keyword in 'for' loop header",
                ERR_MALFORMED_FOR_HEADER,
                "Add 'in' keyword"
            ));
            return;
        }

        List<Token> target = clauseTokens.subList(0, inIndex);
        List<Token> iterable = clauseTokens.subList(inIndex + 1, clauseTokens.size());

        if (target.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                clauseTokens.get(inIndex).startLocation(),
                "Missing target variable before 'in' in 'for' loop header",
                ERR_MALFORMED_FOR_HEADER,
                "Specify loop variable"
            ));
        } else if (iterable.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                clauseTokens.get(inIndex).endLocation(),
                "Missing iterable expression after 'in' in 'for' loop header",
                ERR_MALFORMED_FOR_HEADER,
                "Specify iterable expression"
            ));
        }
    }

    private List<Token> filterTriviaTokens(List<Token> rawTokens) {
        List<Token> filtered = new ArrayList<>(rawTokens.size());
        for (Token t : rawTokens) {
            if (!t.isTrivia()) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    private List<Token> filterPythonTokens(List<Token> rawTokens) {
        List<Token> filtered = new ArrayList<>(rawTokens.size());
        for (Token t : rawTokens) {
            if (t.tokenType() != TokenType.WHITESPACE && t.tokenType() != TokenType.COMMENT) {
                filtered.add(t);
            }
        }
        return filtered;
    }
}
