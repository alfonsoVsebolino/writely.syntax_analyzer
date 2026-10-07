# 14. Control Structure Header Validation Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

Control-flow constructs (`if`, `elif`, `else`, `while`, `for`, `switch`, `try`, `except`, `finally`, `with`, `match`, `case`) represent fundamental branching and looping primitives in modern programming languages. However, their structural syntax, delimiter requirements, and clause structures diverge significantly across language paradigms:

- **Java and C++**:
  - Control-flow constructs (`if`, `for`, `while`, `switch`) require strictly balanced enclosing parentheses `(...)` bounding the condition expression or loop clauses.
  - `if`, `while`, and `switch` headers mandate a non-empty expression enclosed within parentheses: `if (condition)`, `while (condition)`, `switch (selector)`. An empty expression (`if ()`) constitutes a malformed header defect.
  - `for` headers support two primary variants:
    1. Traditional 3-part loops (`for (init; condition; update)`) which mandate exactly two top-level clause-separating semicolons (permitting empty clauses such as `for (;;)`).
    2. Enhanced / range-based loops (`for (Type var : iterable)` in Java or `for (auto&& x : container)` in C++) requiring a target variable declaration, a top-level colon `:`, and an iterable sequence.
  - C++ introduces specific structural extensions, such as `if constexpr (...)` (compile-time branch evaluation) and C++17 selection statements with initializers (`if (init; condition)`), wherein top-level semicolons can validly occur inside the condition header prior to the closing parenthesis.
  - Keywords appearing in member-access contexts (`obj.if`, `ptr->while`, `Scope::switch`) represent identifier naming or member selections, not control headers.

- **Python**:
  - Python uses indentation-based block scoping and mandates a terminating colon (`:`) at the end of all compound control headers: `if`, `elif`, `else`, `while`, `for`, `try`, `except`, `finally`, `with`, `match`, and `case`.
  - Enclosing parentheses around condition expressions are strictly optional (`if x > 0:` and `if (x > 0):` are both valid).
  - Specific headers mandate expressions (`if`, `elif`, `while`, `match`, `case`, `with`), whereas `else:`, `try:`, and `finally:` strictly take no condition expression.
  - `for` loop headers require an explicit loop target, the `in` keyword, and an iterable expression (`for <target> in <iterable>:`).
  - Comprehensions (`[x for x in seq if x > 0]`, `{k: v for k, v in d.items()}`) and ternary conditional expressions (`x if cond else y`) feature `for`, `if`, and `else` keywords within expression contexts (non-zero delimiter depth or non-statement-start positions) and must not be misidentified as compound block headers.

In alignment with Issue #23 and architectural requirements, a specialized syntax checker under `com.writely.syntax_analyzer.core.check` is needed to validate control-structure headers across Java, C++, and Python with exact coordinate precision, robust recovery, and zero false positives.

## Decisions

1. **Syntax Checker Implementation**:
   Implement `ControlStructureHeaderChecker` implementing `SyntaxChecker` under package `com.writely.syntax_analyzer.core.check`, operating under `CheckCategory.CONTROL_HEADER`.

2. **Standard Diagnostic Codes**:
   - `ERR_MALFORMED_CONTROL_HEADER`: Malformed header or missing condition/selector expression in control structures (`if`, `while`, `switch` in Java/C++; `if`, `elif`, `while`, `match`, `case`, `with` in Python; or forbidden expressions in Python `else`, `try`, `finally`).
   - `ERR_MISSING_HEADER_DELIMITER`: Missing enclosing parentheses in Java/C++ (`if condition {` missing `(` or `)`).
   - `ERR_MISSING_HEADER_COLON`: Missing terminating colon on Python compound statement headers (`if`, `elif`, `else`, `while`, `for`, `try`, `except`, `finally`, `with`, `match`, `case`).
   - `ERR_MALFORMED_FOR_HEADER`: Malformed `for` loop header (missing or excessive semicolons in Java/C++ `for (;;) `, missing target or iterable in enhanced/range `for`, or missing `in` / target / iterable in Python `for x in seq:`).

3. **Core Language Invariants and Disambiguation**:
   - **Java/C++ Delimiter Scanning**:
     - Token-stream scanning tracks balanced delimiter depth (`parenDepth`, `bracketDepth`, `braceDepth`) from the control keyword.
     - If the immediate token following `if`, `while`, `switch`, or `for` (accounting for C++ `constexpr`) is not `(`, `ERR_MISSING_HEADER_DELIMITER` is emitted at the unexpected token's location, recovering forward to the next block `{` or terminator `;`.
     - If `(` is present, tokens are scanned until the matching closing `)`. If an unclosed header encounters an un-nested `{` (or un-nested `;` without closing parenthesis lookahead), `ERR_MISSING_HEADER_DELIMITER` is emitted at the boundary token.
     - Semicolons inside C++17 `if (init; condition)` are disambiguated using lookahead to verify the presence of a closing `)` before `{`, preventing false-positive delimiter errors.
     - For `for` loops, top-level semicolons and colons are analyzed: top-level `:` without preceding ternary `?` indicates enhanced/range `for`; otherwise, top-level semicolons must equal exactly 2.
     - Keywords preceded by `.`, `->`, or `::` are ignored as member accesses.
   - **Python Header Scanning**:
     - Compound control headers are detected at statement start (accounting for optional `async` prefix) when delimiter nesting depth is 0.
     - Comprehensions (`[...]`, `{...}`, `(...)`) and inline conditionals are isolated by delimiter depth tracking (`bracketDepth > 0` or `parenDepth > 0`) and statement-start tracking, ensuring zero false positives.
     - If no colon `:` terminates the header before `NEWLINE`, `INDENT`, `DEDENT`, or EOF, `ERR_MISSING_HEADER_COLON` is emitted at the end of the header expression.
     - In Python `for` loops, the presence of the `in` keyword at top level is validated before checking for colons, emitting `ERR_MALFORMED_FOR_HEADER` when omitted.
     - Condition-less statements (`else:`, `try:`, `finally:`) strictly reject accompanying condition expressions.

4. **Coordinate Precision and Clean Error Recovery**:
   - All emitted diagnostics contain 1-based line and column coordinates pinpointing the exact defect location (e.g., location of the missing `(`, boundary of the unclosed `)`, end of Python header missing `:`, or location of malformed `for` clauses).
   - Non-cascading synchronization: after detecting a defective header, the scanner synchronizes to the statement/block boundary (`{` or next line) so subsequent control structures in the program are checked independently.
   - Non-null preconditions (`payload`, `tokens`), unmodifiable empty list handling for blank token streams, and monotonic sorting by `SourceLocation`.

5. **Separation of Concerns**:
   - Excludes semantic control-flow analysis (such as reachability, dead-code detection, or loop termination proofs).
   - Complements statement termination checks (`StatementTerminatorAndBlockChecker`) and delimiter pair checks (`DelimiterMatchingChecker`) without overlapping diagnostic codes.

## Consequences

- Full structural validation of control-structure headers across Java, C++, and Python matching all specifications of Issue #23.
- Zero false positives on valid Java, Python, and C++ code, including modern C++17 if-with-initializer, C++ `constexpr`, Java enhanced for-each, and Python comprehensions.
- High coordinate accuracy adhering to the ubiquitous language defined in `GLOSSARY.md`.
- Robust error recovery enabling multiple sequential defective headers to be diagnosed cleanly in a single analysis pass.
