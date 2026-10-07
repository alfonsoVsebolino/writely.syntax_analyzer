package com.writely.syntax_analyzer.core.check;

import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Syntax checker verifying balanced delimiter pairs (parentheses, brackets, curly braces)
 * across supported programming languages in accordance with ADR 0005.
 */
public class DelimiterMatchingChecker implements SyntaxChecker {

    public static final String ERR_MISMATCHED_DELIMITER = "ERR_MISMATCHED_DELIMITER";
    public static final String ERR_UNEXPECTED_CLOSING_DELIMITER = "ERR_UNEXPECTED_CLOSING_DELIMITER";
    public static final String ERR_UNCLOSED_DELIMITER = "ERR_UNCLOSED_DELIMITER";

    private static final Set<String> OPENING_DELIMITERS = Set.of("(", "[", "{");
    private static final Set<String> CLOSING_DELIMITERS = Set.of(")", "]", "}");

    private static final int MAX_LOOKBACK_LEVELS = 3;

    public DelimiterMatchingChecker() {
    }

    @Override
    public CheckCategory category() {
        return CheckCategory.DELIMITER_MATCH;
    }

    @Override
    public List<Diagnostic> check(SourcePayload payload, List<Token> tokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");

        List<Diagnostic> diagnostics = new ArrayList<>();
        List<Token> stack = new ArrayList<>();

        for (Token token : tokens) {
            if (token.isTrivia() || token.tokenType() != TokenType.DELIMITER) {
                continue;
            }

            String lexeme = token.lexeme();
            if (OPENING_DELIMITERS.contains(lexeme)) {
                stack.add(token);
            } else if (CLOSING_DELIMITERS.contains(lexeme)) {
                if (stack.isEmpty()) {
                    diagnostics.add(Diagnostic.error(
                        CheckCategory.DELIMITER_MATCH,
                        token.startLocation(),
                        String.format("Unexpected closing delimiter '%s' with no matching opening delimiter", lexeme),
                        ERR_UNEXPECTED_CLOSING_DELIMITER,
                        String.format("Remove unexpected '%s'", lexeme)
                    ));
                } else {
                    int topIdx = stack.size() - 1;
                    Token top = stack.get(topIdx);

                    if (isDelimiterPair(top.lexeme(), lexeme)) {
                        stack.remove(topIdx);
                    } else {
                        // Check recovery lookback window (up to 3 levels below top)
                        int matchIdx = -1;
                        int maxLookback = Math.min(MAX_LOOKBACK_LEVELS, topIdx);
                        for (int i = 1; i <= maxLookback; i++) {
                            Token candidate = stack.get(topIdx - i);
                            if (isDelimiterPair(candidate.lexeme(), lexeme)) {
                                matchIdx = topIdx - i;
                                break;
                            }
                        }

                        if (matchIdx != -1) {
                            // Skipped open delimiters reported as unclosed
                            for (int j = matchIdx + 1; j <= topIdx; j++) {
                                Token skipped = stack.get(j);
                                String expected = matchingClosing(skipped.lexeme());
                                diagnostics.add(Diagnostic.error(
                                    CheckCategory.DELIMITER_MATCH,
                                    skipped.startLocation(),
                                    String.format("Unclosed delimiter '%s' at line %d, column %d; expected matching '%s' before '%s'",
                                        skipped.lexeme(),
                                        skipped.startLocation().line(),
                                        skipped.startLocation().column(),
                                        expected,
                                        lexeme),
                                    ERR_UNCLOSED_DELIMITER,
                                    String.format("Insert matching '%s'", expected)
                                ));
                            }
                            // Pop skipped tokens plus the matched token
                            while (stack.size() > matchIdx) {
                                stack.remove(stack.size() - 1);
                            }
                        } else {
                            // Mismatched delimiter
                            String expected = matchingClosing(top.lexeme());
                            diagnostics.add(Diagnostic.error(
                                CheckCategory.DELIMITER_MATCH,
                                token.startLocation(),
                                String.format("Mismatched delimiter: expected '%s' to match '%s' at line %d, column %d, but found '%s'",
                                    expected,
                                    top.lexeme(),
                                    top.startLocation().line(),
                                    top.startLocation().column(),
                                    lexeme),
                                ERR_MISMATCHED_DELIMITER,
                                String.format("Replace '%s' with '%s'", lexeme, expected)
                            ));
                            stack.remove(topIdx);
                        }
                    }
                }
            }
        }

        // Reached EOF with unclosed delimiters remaining on stack
        for (Token unclosed : stack) {
            String expected = matchingClosing(unclosed.lexeme());
            diagnostics.add(Diagnostic.error(
                CheckCategory.DELIMITER_MATCH,
                unclosed.startLocation(),
                String.format("Unclosed delimiter '%s' at line %d, column %d; expected matching '%s'",
                    unclosed.lexeme(),
                    unclosed.startLocation().line(),
                    unclosed.startLocation().column(),
                    expected),
                ERR_UNCLOSED_DELIMITER,
                String.format("Insert matching '%s'", expected)
            ));
        }

        return Collections.unmodifiableList(diagnostics);
    }

    static boolean isDelimiterPair(String open, String close) {
        return ("(".equals(open) && ")".equals(close))
            || ("[".equals(open) && "]".equals(close))
            || ("{".equals(open) && "}".equals(close));
    }

    static String matchingClosing(String open) {
        return switch (open) {
            case "(" -> ")";
            case "[" -> "]";
            case "{" -> "}";
            default -> throw new IllegalArgumentException("Unknown opening delimiter: " + open);
        };
    }
}
