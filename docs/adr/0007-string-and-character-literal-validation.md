# 7. String and Character Literal Validation Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

Static syntactical analysis requires language-aware validation of string and character literals across Java, Python, and C++. Defects such as unclosed quote delimiters, empty character literals, multi-character character literals, and illegal escape sequences must be detected before or during AST construction.

Different programming languages enforce distinct lexical rules for literals:
- Java supports single-line string literals `"..."`, multi-line text blocks `"""..."""`, and character literals `'...'` with octal (`\0`..`\377`), unicode (`\uXXXX`), and standard escapes.
- Python supports single-quoted `'...'` and double-quoted `"..."` strings, triple-quoted multi-line strings (`'''...'''`, `"""..."""`), and string prefixes (`r`, `f`, `b`, `u`). Raw strings (`r`/`R` prefix) treat backslashes as literal characters and must be exempt from escape validation.
- C++ supports standard string literals `"..."`, character literals `'...'`, and raw string literals `R"delim(...)delim"`, which are also exempt from escape sequence validation.

## Decisions

1. **Syntax Checker Implementation**:
   Implement `StringAndCharacterLiteralChecker` implementing `SyntaxChecker` under `com.writely.syntax_analyzer.core.check`, operating under `CheckCategory.LITERAL_SYNTAX`.

2. **Standard Diagnostic Codes**:
   - `ERR_UNCLOSED_STRING_LITERAL`: Unclosed single/double quotes, unclosed text blocks `"""..."""`, unclosed Python triple quotes `'''`/`"""`, or unclosed C++ raw strings `R"delim(...)delim"`.
   - `ERR_UNCLOSED_CHARACTER_LITERAL`: Character literals missing closing single quote `'` or terminating prematurely.
   - `ERR_EMPTY_CHARACTER_LITERAL`: Empty character literal `''` in Java/C++.
   - `ERR_INVALID_CHARACTER_LITERAL`: Multi-character literal `'ab'` in Java/Python (when used as char) or empty in C++.
   - `ERR_ILLEGAL_ESCAPE_SEQUENCE`: Invalid escape sequences in string or char literals (e.g., `\c` in Java/C++, malformed unicode `\u123`, invalid octal `\8`).

3. **Language-Specific Validation Rules**:
   - **Java**:
     - Standard string `"..."` and text block `"""..."""` validation.
     - Character literal `'...'` single-unit enforcement (`''` -> `ERR_EMPTY_CHARACTER_LITERAL`, `'ab'` -> `ERR_INVALID_CHARACTER_LITERAL`).
     - Escape sequence validation: `\b`, `\t`, `\n`, `\f`, `\r`, `\"`, `\'`, `\\`, `\s`, octal `\0`..`\377`, and unicode `\uXXXX` (supporting repeated `u`).
   - **Python**:
     - Single-quoted `'...'`, double-quoted `"..."`, and triple-quoted `'''...'''`/`"""..."""` validation.
     - Prefix awareness: prefixes containing `r` or `R` are treated as raw strings and exempt from escape validation.
     - Non-raw escapes validated: `\a`, `\b`, `\f`, `\n`, `\r`, `\t`, `\v`, `\\`, `\'`, `\"`, octal `\ooo`, hex `\xhh`, unicode `\uxxxx`/`\Uxxxxxxxx`, and named unicode `\N{...}`.
   - **C++**:
     - Standard `"..."` and character literal `'...'` validation.
     - Raw strings `R"delim(...)delim"` validated for matching opening and closing delimiter boundaries, and exempt from escape validation.
     - Non-raw escapes validated: simple escapes (`\'`, `\"`, `\?`, `\\`, `\a`, `\b`, `\f`, `\n`, `\r`, `\t`, `\v`), octal `\ooo`, hex `\xh...`, and universal character names `\uXXXX`/`\UXXXXXXXX`.

4. **Coordinate Precision and Suggested Fixes**:
   Diagnostics are emitted using `Diagnostic.error(CheckCategory.LITERAL_SYNTAX, location, message, code, suggestedFix)`.
   - Literal delimiter defects pinpoint the token start location.
   - Escape sequence errors pinpoint the exact 1-based `SourceLocation` of the offending backslash `\`.
   - Every diagnostic includes an actionable `suggestedFix`.

5. **Safety and Recovery**:
   - Null validation enforced for both payload and token parameters.
   - Clean recovery across multiple defects in a single file or snippet, ensuring that defective literals do not cascade into false positives for subsequent tokens.

## Consequences

- Consistent literal validation contract across all supported languages.
- Eliminates false positive escape errors on raw string formats in Python and C++.
- Delivers pinpoint diagnostics and automated fix hints directly to downstream diagnostic reporting (Issue #16) and GUI presentation.
