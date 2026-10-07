# 5. Delimiter and Bracket Matching Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

The analyzer requires robust verification of balanced structural delimiters: parentheses `()`, square brackets `[]`, and curly braces `{}` across Java, Python, and C++.

## Decisions

1. **Syntax Checker Contract**:
   ```java
   public interface SyntaxChecker {
       CheckCategory category();
       List<Diagnostic> check(SourcePayload payload, List<Token> tokens);
   }
   ```
   Located in `com.writely.syntax_analyzer.core.check`.

2. **Delimiter Types**:
   - Parentheses: `(` and `)`
   - Square brackets: `[` and `]`
   - Curly braces: `{` and `}`
   Angle brackets `<>` are treated as operators/comparison/generics, not bracket delimiters.

3. **Trivia & Literal Isolation**:
   Delimiters contained in comments (`//`, `/* */`, `#`) and literals (`"..."`, `'...'`) are ignored by consuming the token stream produced by `Tokenizer` where trivia and literal tokens are classified.

4. **Error Diagnostics & Codes**:
   - `MISMATCHED_DELIMITER`: Closing delimiter does not match top of stack (e.g. `(]` or `{)`).
   - `UNEXPECTED_CLOSING_DELIMITER`: Closing delimiter with empty stack (e.g. `x = 10)`).
   - `UNCLOSED_DELIMITER`: Reached EOF with unmatched open delimiters remaining on the stack.
   All diagnostics carry `CheckCategory.DELIMITER_MATCH`, `Severity.ERROR`, precise 1-based `SourceLocation`, and expected vs observed details.

5. **Recovery Strategy**:
   When an unexpected delimiter occurs, check if it matches an open delimiter within a 3-frame lookback window in the stack. If found, report the skipped open delimiters as unclosed and recover. Otherwise, report `UNEXPECTED_CLOSING_DELIMITER` and preserve the stack to prevent cascading spurious errors.

6. **Python Indentation Scope Boundary**:
   Python indentation-based block delimiters (`INDENT`/`DEDENT` and colons `:`) are handled under `STATEMENT_TERMINATOR` (Issue #21). Issue #19 strictly covers explicit bracket pairs `()`, `[]`, `{}` across all three languages.
