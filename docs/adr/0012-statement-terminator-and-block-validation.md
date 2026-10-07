# 12. Statement Terminator and Block Validation Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

Different programming paradigms enforce distinct syntactic boundaries for statements and block definitions:
- **Java and C++**: Imperative statement sequencing relies on explicit statement terminators (`;`). However, their structural placement differs fundamentally:
  - In Java, declarations, variable initializations, expressions, return/throw/break/continue/yield/assert statements, and `do-while` loops require a trailing `;`. Compound blocks enclosed in curly braces `{}` (classes, interfaces, records, enums, methods, constructors, `if`, `for`, `while`, `switch`, `try`) never require a trailing semicolon.
  - In C++, semicolons are strictly required on statements, variable and function declarations, `do-while` constructs, and critically, after `class`, `struct`, and `enum` definitions (`class Foo {};`). In contrast, function definitions with a body (`void foo() {}`) and namespace blocks (`namespace N {}`) must not require a trailing semicolon.
- **Python**: Enforces indentation-sensitive blocks and header colons:
  - Semicolons are optional statement separators, never mandatory terminators. A universal semicolon rule is prohibited.
  - Compound statement headers (`def`, `class`, `if`, `elif`, `else`, `for`, `while`, `try`, `except`, `finally`, `with`, `match`, `case`) strictly mandate a terminating colon (`:`).
  - Multi-line compound blocks following a header must be introduced by an indented block (`INDENT` token).
  - Unaligned indentation levels or indents without an enclosing compound header constitute syntax errors.
  - Dedentation must match an existing indentation level previously recorded on the indentation stack.

A dedicated syntax checker is required under `com.writely.syntax_analyzer.core.check` to validate statement termination and block structure across all supported languages, preventing false positives and ensuring clean, non-cascading error recovery.

## Decisions

1. **Syntax Checker Implementation**:
   Implement `StatementTerminatorAndBlockChecker` implementing `SyntaxChecker` under `com.writely.syntax_analyzer.core.check`, operating under `CheckCategory.STATEMENT_TERMINATOR`.

2. **Standard Diagnostic Codes and Actions**:
   - `ERR_MISSING_SEMICOLON` (Java, C++): Missing `;` after statements, declarations, return/throw/break/continue/yield/assert, `do-while`, package/import (Java), and C++ `class`, `struct`, and `enum` definitions. Suggested fix: `"Insert ';'"` with exact location at `previousToken.endLocation()`.
   - `ERR_MISSING_COLON` (Python): Missing `:` at the end of compound statement headers (`def`, `class`, `if`, `elif`, `else`, `for`, `while`, `try`, `except`, `finally`, `with`, `match`, `case`). Suggested fix: `"Insert ':'"` with exact location at the end of the header expression (`lastHeaderToken.endLocation()`).
   - `ERR_EXPECTED_INDENTED_BLOCK` (Python): Compound statement header terminated by `:` (or missing `:`) not followed by an indented block (`INDENT` token). Suggested fix: `"Indent statement block"` with location at the following statement's start location or header end location at EOF.
   - `ERR_UNEXPECTED_INDENT` (Python): Token indented without an enclosing compound header or misaligned indentation. Suggested fix: `"Align indentation with enclosing block"` with location at `indentToken.startLocation()`.
   - `ERR_UNMATCHED_DEDENT` (Python): `DEDENT` token that fails to align with any outer indentation level on the indentation stack. Suggested fix: `"Realign dedent with prior block"` with location at `misalignedToken.startLocation()`.

3. **Core Boundary Invariants & False-Positive Prevention**:
   - **No Universal Semicolon Mandate**: Python statements never mandate semicolons. Semicolons in Python are treated as optional delimiters.
   - **Loop Header Separators**: Internal semicolons in Java and C++ `for` loops (`for (int i = 0; i < 10; i++)`) serve as loop header separators and are not flagged as statement terminators.
   - **Block Semicolon Disambiguation**: Java `{}` blocks (classes, methods, control structures) never mandate trailing semicolons. In C++, class, struct, and enum definitions require trailing semicolons, while function definitions with a body and namespace blocks do not.
   - **Parenthesized Python Expressions**: Multi-line expressions enclosed in parentheses `(...)`, square brackets `[...]`, or braces `{...}` bypass newline indentation rules, isolated by bracket depth tracking in the tokenizer and checker.
   - **Trivia Isolation**: Semicolons and colons occurring inside comments or character/string literals are isolated by lexical token types and ignored during statement termination checks.

4. **Integration and Clean Error Recovery**:
   - For Java and C++, leverage the existing parser engines (`JavaParser` and `CppParser`) to perform robust, AST-level statement analysis and non-cascading lookahead recovery, translating internal parser terminator diagnostics into canonical `ERR_MISSING_SEMICOLON` diagnostics.
   - Enhanced `JavaTokenStream` recovery with statement-start lookahead and line-boundary detection, ensuring that missing semicolons do not consume subsequent statement identifiers.
   - For Python, execute lexical token stream analysis over filtered non-trivia tokens, maintaining an explicit indentation stack (`Deque<Integer>`) and compound header tracking to detect missing colons, missing suites, unexpected indents, and unmatched dedents in a single pass.
   - Strictly validate non-null preconditions (`payload`, `tokens`), return empty unmodifiable lists on empty token streams, and sort all emitted diagnostics monotonically by source location.

## Consequences

- Comprehensive, language-specific statement terminator and block structure validation for Java, C++, and Python.
- Exact coordinate precision adhering to 1-based source location contracts.
- High-fidelity false positive prevention for `for` loops, Python brackets, and block curly braces.
- Robust, single-pass non-cascading error recovery enabling multiple sequential defects to be flagged cleanly in a single analysis run.
