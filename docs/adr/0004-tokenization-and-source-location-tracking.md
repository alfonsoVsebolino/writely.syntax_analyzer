# 4. Tokenization and Source-Location Tracking Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

All downstream syntax checks (delimiter balancing, literal checking, statement terminators, operator syntax, header structure, identifier validation) depend on an accurate, language-aware stream of tokens. Tokens must track exact 1-based line and column spans and ensure that code-like symbols inside comments and string literals are not falsely analyzed as executable syntax.

## Decisions

1. **Tokenizer Interface**:
   ```java
   public interface Tokenizer {
       List<Token> tokenize(SourcePayload payload);
   }
   ```
   Located in `com.writely.syntax_analyzer.core.tokenization`.

2. **Coordinate Tracking**:
   `SourceCharStream` tracks:
   - `charOffset` (0-based)
   - `line` (1-based, incrementing on `\n`)
   - `column` (1-based, resetting to 1 after newline)
   Tabs are handled consistently (advancing column by 1 char offset).

3. **Language Implementations**:
   - `JavaTokenizer`: Java keywords, operators, delimiters, numeric/string/char literals with escape handling, single-line (`//`) and multi-line (`/* */`) comments.
   - `PythonTokenizer`: Python keywords, operators (`**`, `//`, `:=`, etc.), delimiters, indentation tracking (`INDENT`, `DEDENT`, `NEWLINE`), single/double and triple-quoted (`"""`, `'''`) strings, comments (`#`).
   - `CppTokenizer`: C++ keywords, operators (`::`, `->`, `<<`, `>>`, etc.), delimiters, preprocessor directives, comments.

4. **Trivia & Trivia Isolation**:
   Comments and whitespace are classified as trivia (`isTrivia() == true`). Downstream checkers filter out trivia tokens so delimiters, colons, or operators inside comments are never analyzed as active syntax.

5. **Error & Malformed Token Retention**:
   Unterminated string or character literals emit tokens with exact spans so downstream diagnostic checkers (e.g. Issue #20) can report precise errors rather than terminating the lexing process.
