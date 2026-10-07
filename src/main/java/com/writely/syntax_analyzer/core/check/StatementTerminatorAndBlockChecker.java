package com.writely.syntax_analyzer.core.check;

import com.writely.syntax_analyzer.adapter.ParseResult;
import com.writely.syntax_analyzer.adapter.cpp.CppParser;
import com.writely.syntax_analyzer.adapter.java.JavaParser;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Syntax checker enforcing language-aware statement termination and block structure rules
 * across Java, C++, and Python in accordance with Issue #21 and ADR 0012.
 */
public class StatementTerminatorAndBlockChecker implements SyntaxChecker {

    public static final String ERR_MISSING_SEMICOLON = "ERR_MISSING_SEMICOLON";
    public static final String ERR_MISSING_COLON = "ERR_MISSING_COLON";
    public static final String ERR_EXPECTED_INDENTED_BLOCK = "ERR_EXPECTED_INDENTED_BLOCK";
    public static final String ERR_UNEXPECTED_INDENT = "ERR_UNEXPECTED_INDENT";
    public static final String ERR_UNMATCHED_DEDENT = "ERR_UNMATCHED_DEDENT";

    private static final Set<String> PYTHON_COMPOUND_KEYWORDS = Set.of(
        "def", "class", "if", "elif", "else", "for", "while",
        "try", "except", "finally", "with", "match", "case"
    );

    public StatementTerminatorAndBlockChecker() {
    }

    @Override
    public CheckCategory category() {
        return CheckCategory.STATEMENT_TERMINATOR;
    }

    @Override
    public List<Diagnostic> check(SourcePayload payload, List<Token> tokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");

        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<Diagnostic> diagnostics = switch (payload.language()) {
            case JAVA -> checkJava(payload, tokens);
            case CPP -> checkCpp(payload, tokens);
            case PYTHON -> checkPython(payload, tokens);
        };

        List<Diagnostic> sorted = new ArrayList<>(diagnostics);
        sorted.sort(Comparator.comparing(Diagnostic::location));
        return Collections.unmodifiableList(sorted);
    }

    private List<Diagnostic> checkJava(SourcePayload payload, List<Token> tokens) {
        JavaParser parser = new JavaParser();
        ParseResult result = parser.parse(payload, tokens);
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (Diagnostic d : result.diagnostics()) {
            if ("ERR_JAVA_MISSING_SEMICOLON".equals(d.code())) {
                if (d.message().contains("for init") || d.message().contains("for condition")) {
                    continue;
                }
                diagnostics.add(Diagnostic.error(
                    CheckCategory.STATEMENT_TERMINATOR,
                    d.location(),
                    d.message(),
                    ERR_MISSING_SEMICOLON,
                    "Insert ';'"
                ));
            }
        }
        return diagnostics;
    }

    private List<Diagnostic> checkCpp(SourcePayload payload, List<Token> tokens) {
        CppParser parser = new CppParser();
        ParseResult result = parser.parse(payload, tokens);
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (Diagnostic d : result.diagnostics()) {
            if (CppParser.ERR_CPP_MISSING_SEMICOLON.equals(d.code())) {
                if (d.message().contains("for init") || d.message().contains("for condition")) {
                    continue;
                }
                diagnostics.add(Diagnostic.error(
                    CheckCategory.STATEMENT_TERMINATOR,
                    d.location(),
                    d.message(),
                    ERR_MISSING_SEMICOLON,
                    "Insert ';'"
                ));
            }
        }
        return diagnostics;
    }

    private List<Diagnostic> checkPython(SourcePayload payload, List<Token> rawTokens) {
        List<Token> tokens = filterPythonTokens(rawTokens);
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        Deque<Integer> indentStack = new ArrayDeque<>();
        indentStack.push(0);

        Token expectingIndentHeader = null;
        boolean atStatementStart = true;

        int i = 0;
        while (i < tokens.size()) {
            Token tok = tokens.get(i);

            if (tok.tokenType() == TokenType.NEWLINE) {
                i++;
                atStatementStart = true;
                continue;
            }

            if (tok.tokenType() == TokenType.INDENT) {
                i++;
                int indent = tok.lexeme().length();
                if (expectingIndentHeader != null) {
                    indentStack.push(indent);
                    expectingIndentHeader = null;
                } else {
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.STATEMENT_TERMINATOR,
                        tok.startLocation(),
                        "Unexpected indentation",
                        ERR_UNEXPECTED_INDENT,
                        "Align indentation with enclosing block"
                    ));
                    indentStack.push(indent);
                }
                atStatementStart = true;
                continue;
            }

            if (tok.tokenType() == TokenType.DEDENT) {
                while (i < tokens.size() && tokens.get(i).tokenType() == TokenType.DEDENT) {
                    i++;
                }
                while (i < tokens.size() && tokens.get(i).tokenType() == TokenType.NEWLINE) {
                    i++;
                }
                int targetIndent;
                Token nextTok = null;
                if (i < tokens.size()) {
                    nextTok = tokens.get(i);
                    targetIndent = nextTok.startLocation().column() - 1;
                } else {
                    targetIndent = 0;
                }

                if (indentStack.contains(targetIndent)) {
                    while (!indentStack.isEmpty() && indentStack.peek() > targetIndent) {
                        indentStack.pop();
                    }
                } else {
                    SourceLocation loc = nextTok != null ? nextTok.startLocation() : tok.startLocation();
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.STATEMENT_TERMINATOR,
                        loc,
                        "Unmatched dedent does not align with any outer indentation level",
                        ERR_UNMATCHED_DEDENT,
                        "Realign dedent with prior block"
                    ));
                    while (!indentStack.isEmpty() && indentStack.peek() > targetIndent) {
                        indentStack.pop();
                    }
                    indentStack.push(targetIndent);
                }
                atStatementStart = true;
                continue;
            }

            // Other code token
            if (expectingIndentHeader != null) {
                diagnostics.add(Diagnostic.error(
                    CheckCategory.STATEMENT_TERMINATOR,
                    tok.startLocation(),
                    "Expected an indented block after '" + expectingIndentHeader.lexeme() + "' statement",
                    ERR_EXPECTED_INDENTED_BLOCK,
                    "Indent statement block"
                ));
                expectingIndentHeader = null;
            }

            Token compoundHeaderKeyword = null;
            if (atStatementStart) {
                if (tok.tokenType() == TokenType.KEYWORD && PYTHON_COMPOUND_KEYWORDS.contains(tok.lexeme())) {
                    compoundHeaderKeyword = tok;
                } else if ("async".equals(tok.lexeme()) && i + 1 < tokens.size()) {
                    Token next = tokens.get(i + 1);
                    if (next.tokenType() == TokenType.KEYWORD && PYTHON_COMPOUND_KEYWORDS.contains(next.lexeme())) {
                        compoundHeaderKeyword = next;
                        i++;
                    }
                }
            }

            if (compoundHeaderKeyword != null) {
                int j = i + 1;
                int parenDepth = 0;
                int bracketDepth = 0;
                int braceDepth = 0;
                Token colonTok = null;
                Token lastHeaderTok = compoundHeaderKeyword;

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
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && t.tokenType() == TokenType.NEWLINE) {
                        break;
                    }
                    lastHeaderTok = t;
                    j++;
                }

                if (colonTok != null) {
                    if (j < tokens.size() && tokens.get(j).tokenType() == TokenType.NEWLINE) {
                        expectingIndentHeader = compoundHeaderKeyword;
                        i = j + 1;
                        atStatementStart = true;
                    } else {
                        expectingIndentHeader = null;
                        i = j;
                        atStatementStart = false;
                    }
                } else {
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.STATEMENT_TERMINATOR,
                        lastHeaderTok.endLocation(),
                        "Missing ':' at end of '" + compoundHeaderKeyword.lexeme() + "' header",
                        ERR_MISSING_COLON,
                        "Insert ':'"
                    ));
                    if (j < tokens.size() && tokens.get(j).tokenType() == TokenType.NEWLINE) {
                        expectingIndentHeader = compoundHeaderKeyword;
                        i = j + 1;
                        atStatementStart = true;
                    } else {
                        expectingIndentHeader = compoundHeaderKeyword;
                        i = j;
                        atStatementStart = true;
                    }
                }
                continue;
            }

            atStatementStart = false;
            if (";".equals(tok.lexeme())) {
                atStatementStart = true;
            }
            i++;
        }

        if (expectingIndentHeader != null) {
            SourceLocation errLoc = tokens.get(tokens.size() - 1).endLocation();
            diagnostics.add(Diagnostic.error(
                CheckCategory.STATEMENT_TERMINATOR,
                errLoc,
                "Expected an indented block after '" + expectingIndentHeader.lexeme() + "' statement",
                ERR_EXPECTED_INDENTED_BLOCK,
                "Indent statement block"
            ));
        }

        return diagnostics;
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
