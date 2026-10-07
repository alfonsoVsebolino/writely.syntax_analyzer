# 2. Shared Analysis Domain Model

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

The Multi-Input Syntactical Analyzer (`writely.syntax_analyzer`) requires a unified domain model to transport source metadata, lexical tokens, syntax trees, diagnostic findings, and summary statistics across architectural layers. The model must bridge:
1. Multi-modal ingestion (single line commands, snippets, uploaded files).
2. Multi-language parser adapters (Java, Python, C++).
3. The orchestration engine and diagnostic check passes.
4. User-facing presentation (JavaFX GUI tree/token views, summary banners, diagnostic tables) and file export (plain text, JSON).

To prevent domain logic leakage, data corruption during concurrent background analysis, and inconsistent error reporting, the domain model must be immutable, strictly validated, and independent of specific parser implementations or UI widgets.

## Decision

We defined an immutable domain model using Java 21 records and enums under `com.writely.syntax_analyzer.domain`:

1. **Language & Input Ingestion**:
   - `Language`: Enum (`JAVA`, `PYTHON`, `CPP`) encapsulating display names, canonical file extensions (`.java`, `.py`, `.cpp`, `.cxx`, `.cc`, `.h`, `.hpp`), and case-insensitive resolution helpers (`fromFileName`, `fromExtension`, `fromNameOrExtension`).
   - `InputMode`: Enum (`SINGLE_LINE`, `CODE_SNIPPET`, `FILE_UPLOAD`) defining source ingestion mechanism.
   - `SourcePayload`: Record holding raw source text, origin identifier, target language, and input mode, with line count and character length metrics.

2. **Source Coordinates & Spans**:
   - `SourceLocation`: Record (`line`, `column`, `charOffset`) enforcing 1-based line/column and 0-based character offset invariants, implementing natural ordering via `Comparable<SourceLocation>`.
   - `SourceSpan`: Record (`start`, `end`) enforcing `start.charOffset() <= end.charOffset()` and monotonic coordinate ordering.

3. **Lexical & AST Representation**:
   - `TokenType`: Universal token category enum (`KEYWORD`, `IDENTIFIER`, `LITERAL_STRING`, `LITERAL_CHAR`, `LITERAL_NUMBER`, `OPERATOR`, `DELIMITER`, `COMMENT`, `INDENT`, `DEDENT`, `NEWLINE`, `WHITESPACE`, `UNKNOWN`).
   - `Token`: Record pairing token type, raw lexeme, source span, and fine-grained category.
   - `SyntaxNode`: Recursive AST node record containing `kind`, `label`, `span`, defensive copies of `children` and `attributes`, and pre-order traversal utilities.

4. **Diagnostics & Aggregation**:
   - `CheckCategory`: Standard inspection scope enum (`DELIMITER_MATCH`, `LITERAL_SYNTAX`, `STATEMENT_TERMINATOR`, `OPERATOR_SYNTAX`, `CONTROL_HEADER`, `IDENTIFIER_NAMING`).
   - `Severity`: Enum (`ERROR`, `WARNING`, `INFO`).
   - `Diagnostic`: Record detailing category, severity, coordinate location, message, diagnostic rule code, and optional suggested fix.
   - `AnalysisStatus`: Outcome status enum (`PASSED`, `FAILED_SYNTAX_ERRORS`).
   - `DiagnosticSummary`: Record summarizing total lines, token count, error count, warning count, flagged lines, valid lines, category breakdown map, and outcome status, computed via `calculate(...)`. `flaggedLines` counts distinct source lines carrying at least one diagnostic; `validLines` is always `totalLines - flaggedLines`.
   - `AnalysisResult`: Complete analysis envelope carrying source payload, tokens, optional syntax tree, diagnostic list, and summary metrics.

## Consequences

- **Immutability & Thread Safety**: All records employ defensive copying of collections (`List.copyOf`, `Map.copyOf`), enabling safe transfer between background parser tasks and JavaFX UI thread without synchronization overhead.
- **Decoupled Architecture**: Parser adapters and diagnostic rules emit pure domain objects without referencing UI elements or external serialization frameworks.
- **Serialization Ready**: Standard records with clean accessor methods provide frictionless mapping to JSON serializers and report formatters.
- **Strict Invariants**: Construction-time checks reject invalid line/column coordinates, inverted spans, or negative summary metrics early at runtime.

## Amendment — 2026-10-07: DiagnosticSummary flagged/valid line totals (#16)

- **Status**: Accepted
- **Date**: 2026-10-07

### Context

The analysis orchestrator introduced by issue #16 produces the first complete
`AnalysisResult`, and its downstream consumers (text/JSON report export #13 and GUI
display #15) need line-level health totals in addition to the existing counts: how many
source lines were flagged by at least one diagnostic, and how many lines are clean. The
summary previously exposed only totals, per-category counts, and the outcome status, so
each consumer would have to recompute these line totals from the diagnostic list.

### Decision

We extended the `DiagnosticSummary` record with two additional components, as described
in section 4 of the original decision:

1. `flaggedLines`: the number of distinct source lines (within the payload range
   `[1, totalLines]`) carrying at least one diagnostic.
2. `validLines`: always `totalLines - flaggedLines`.

`DiagnosticSummary.calculate(...)` derives both values while aggregating the diagnostics
in a single pass. The canonical constructor now rejects negative `flaggedLines` /
`validLines` and any combination where `flaggedLines + validLines != totalLines`,
preserving the ADR's strict-invariants policy. The record's component arity therefore
changed, which is source-incompatible for positional constructor callers; all in-repository
callers were updated in the same change. `Diagnostic` itself is unchanged.

### Consequences

- Summary consumers can read line health totals directly instead of recomputing them,
  keeping the shared model the single source of truth for aggregate metrics.
- The invariant `flaggedLines + validLines == totalLines` is enforced at construction
  time, so invalid summaries fail fast rather than reaching the GUI or exporters.
