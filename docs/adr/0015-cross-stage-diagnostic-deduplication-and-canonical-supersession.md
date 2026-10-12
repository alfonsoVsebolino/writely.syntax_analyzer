# 15. Cross-Stage Diagnostic Deduplication and Canonical Supersession Architecture

- **Status**: Accepted
- **Date**: 2026-10-12

## Context

In `writely.syntax_analyzer`, the end-to-end static analysis pipeline executes in two primary phases within `AnalysisOrchestrator`:
1. **Parser Adapter Phase**: `ParserAdapter.parse(payload, tokens)` constructs language ASTs and emits parser-internal diagnostics (`ERR_JAVA_*`, `CPP_*`, `ERR_*`).
2. **Universal Syntax Checker Phase**: A suite of domain-specific `SyntaxChecker` implementations (`DelimiterMatchingChecker`, `StatementTerminatorAndBlockChecker`, `ControlStructureHeaderChecker`, `OperatorSyntaxChecker`, `StringAndCharacterLiteralChecker`, `IdentifierSyntaxChecker`) inspect token streams and AST structures, emitting canonical cross-language diagnostics.

Prior to this decision, `AnalysisOrchestrator` combined `ParseResult.diagnostics()` directly with checker diagnostics and passed them to `DiagnosticAggregator`. Because `DiagnosticAggregator` strictly deduplicated on exact equality of `(category, code, line, column, message)`, parser-internal diagnostics (e.g., `ERR_JAVA_MISSING_SEMICOLON`, `CPP_MISSING_SEMICOLON`, `ERR_JAVA_UNCLOSED_PARENTHESIS`, `ERR_JAVA_OPERATOR_SYNTAX`) and canonical checker diagnostics (e.g., `ERR_MISSING_SEMICOLON`, `ERR_UNCLOSED_DELIMITER`, `ERR_INVALID_OPERATOR_SEQUENCE`) pointing to the identical source defect at the same location both survived. This caused duplicate diagnostic cards to render in the GUI and inflated error counts in category breakdown summaries.

Furthermore, UI cards and category pills displayed raw internal error identifiers (e.g., `ERR_JAVA_MISSING_SEMICOLON`, `STATEMENT_TERMINATOR`), conflicting with UX requirements for human-readable, non-underscored descriptions.

## Decisions

1. **Cross-Stage Deduplication and Canonical Supersession in `AnalysisOrchestrator`**:
   - `AnalysisOrchestrator` isolates `parseDiagnostics` from `checkerDiagnostics`.
   - When merging stages, canonical checker diagnostics take precedence over parser-internal diagnostics sharing `(CheckCategory, line, column)`:
     ```java
     Set<CategoryLocationKey> checkerKeys = checkerDiagnostics.stream()
         .map(d -> new CategoryLocationKey(d.category(), d.location().line(), d.location().column()))
         .collect(Collectors.toSet());
     ```
   - Any parser diagnostic whose `(category, line, column)` matches a canonical checker diagnostic is superseded and suppressed.
   - Non-colliding parser diagnostics (syntax defects caught only during AST parsing where no specialized checker emitted a finding) are preserved.

2. **Full Category Coverage**:
   - Canonical supersession operates uniformly across all six `CheckCategory` domains:
     - `STATEMENT_TERMINATOR`: Canonical `ERR_MISSING_SEMICOLON`, `ERR_MISSING_COLON`, `ERR_EXPECTED_INDENTED_BLOCK`, etc., supersede parser terminator defects.
     - `DELIMITER_MATCH`: Canonical `ERR_UNCLOSED_DELIMITER`, `ERR_MISMATCHED_DELIMITER` supersede parser unclosed bracket/paren errors.
     - `CONTROL_HEADER`: Canonical `ERR_MISSING_HEADER_DELIMITER`, `ERR_MALFORMED_CONTROL_HEADER` supersede parser control header defects.
     - `OPERATOR_SYNTAX`: Canonical `ERR_INVALID_OPERATOR_SEQUENCE`, `ERR_MISSING_OPERAND` supersede parser operator syntax errors.
     - `IDENTIFIER_NAMING`: Canonical `ERR_KEYWORD_AS_IDENTIFIER`, `ERR_ILLEGAL_IDENTIFIER_CHAR` supersede parser expected-identifier errors.
     - `LITERAL_SYNTAX`: Canonical string/character literal diagnostics.

3. **UX-Friendly Slug Formatting in GUI Layer**:
   - The desktop presentation layer (`WorkspaceController`) transforms raw diagnostic codes and category enums into user-friendly title-cased labels:
     - Category breakdown pills: `formatCategoryName(category)` converts enum names (e.g., `STATEMENT_TERMINATOR` -> `"Statement Terminator"`).
     - Diagnostic card badges: `formatErrorCode(code, language)` strips `ERR_` prefixes, resolves `<Language Slug>` (`Java`, `Cpp`, `Python`), and title-cases the error slug without underscores (e.g., `ERR_JAVA_MISSING_SEMICOLON` -> `"Java Missing Semicolon"`).
   - `renderDiagnostics` maintains a `seenCards` check on `(line, column, category)` as UI defensive deduplication.

## Consequences

- Completely eliminates duplicate diagnostic findings and redundant UI cards across Java, C++, and Python without discarding genuine non-colliding parser diagnostics.
- Preserves single responsibility: domain checkers own canonical rule validation; parser adapters own AST generation and internal recovery; `AnalysisOrchestrator` handles pipeline stage collation.
- Standardizes user-facing error presentations across the desktop GUI without exposing raw enum names or underscores.
- All 556 existing core, adapter, regression, and UI tests pass deterministically.
