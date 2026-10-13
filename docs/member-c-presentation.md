# Member C Presentation Guide: Universal Syntax Checking & Orchestration

## 1. Role & Presentation Metadata
* **Assigned Stage**: Pipeline Phase 3 & Phase 4 (Universal Syntax Checking Suite, Orchestrator Lifecycle & ADR 0015 Canonical Supersession).
* **Covered Slides**: Slide 10, Slide 11, Slide 12 in [`writely-architecture-presentation.md`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/docs/lecture/writely-architecture-presentation.md).
* **Target Timing**: ~4 to 5 minutes.
* **Core Mission**: Explain the 6 universal syntax checkers, capability gating, the orchestrator execution lifecycle, and ADR 0015 diagnostic deduplication.

---

## 2. Intuitive Mental Model
> **The Building Inspector & Deduplicating Dispatcher Analogy:**
> In building construction, the architect and framing crew (the parser from Member B) construct the building and report structural anomalies.
> * **The Problem**: A structural crew only sees whether a beam fits. Specialized city inspectors (fire, electrical, plumbing) are needed to check specific safety codes.
> * **Universal Checkers (`core/check/`)** are the specialized safety inspectors. They don't re-frame the building; they walk through inspecting specific hazards—such as checking that all electrical circuits have grounds (bracket matching) or that rooms have marked exits (statement terminators).
> * **ADR 0015 Canonical Supersession** is the central dispatcher. If both the framing crew and the fire inspector flag the same missing fire exit on the 3rd floor, the dispatcher suppresses the raw framing note and keeps the inspector's detailed, actionable citation with instructions on how to fix it!

---

## 3. Relevant Codebase Files & Deep Overview

### 3.1. The Universal Syntax Checking Suite (`core/check/`)
* [`SyntaxChecker.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/check/SyntaxChecker.java):
  * **Role**: Common interface for all modular syntax checkers.
  * **Contract**:
    ```java
    public interface SyntaxChecker {
        CheckCategory category();
        List<Diagnostic> check(List<Token> tokens, Optional<SyntaxNode> astRoot, SourcePayload payload);
    }
    ```
* The 6 Modular Checkers:
  1. [`DelimiterMatchingChecker.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/check/DelimiterMatchingChecker.java):
     * Uses a stack algorithm to verify matching pairs for parentheses `()`, brackets `[]`, and braces `{}`.
     * Flags unclosed openers, unexpected closers, and mismatched pairs with exact line/column locations.
  2. [`StringAndCharacterLiteralChecker.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/check/StringAndCharacterLiteralChecker.java):
     * Detects unterminated string quotes, invalid escape sequences (e.g. invalid `\q`), and multi-character char literals.
  3. [`StatementTerminatorAndBlockChecker.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/check/StatementTerminatorAndBlockChecker.java):
     * Checks missing semicolons in statement-terminated languages (Java, C++) and verifies block closure boundaries.
  4. [`OperatorSyntaxChecker.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/check/OperatorSyntaxChecker.java):
     * Detects illegal adjacent binary operators (e.g. `a +* b`) and dangling operators missing operands.
  5. [`ControlStructureHeaderChecker.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/check/ControlStructureHeaderChecker.java):
     * Validates headers of `if`, `while`, `for`, `switch` statements (e.g. missing parenthesized conditions).
  6. [`IdentifierSyntaxChecker.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/check/IdentifierSyntaxChecker.java):
     * Validates naming conventions, illegal start characters (e.g. identifier starting with a digit), and reserved keyword collisions.

### 3.2. Analysis Orchestrator & Lifecycle (`core/analysis/`)
* [`AnalysisOrchestrator.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/analysis/AnalysisOrchestrator.java):
  * **Role**: Central conductor coordinating the entire static analysis pipeline.
  * **Step-by-Step Lifecycle**:
    1. Ingestion / validation of incoming `SourcePayload`.
    2. Resolves registered `ParserAdapter` via `ParserAdapterRegistry`.
    3. Calls `adapter.tokenize(payload)` to produce `List<Token>`.
    4. Calls `adapter.parse(payload, tokens)` to obtain `ParseResult` (partial AST + internal diagnostics).
    5. Queries `adapter.ruleCapabilities().supportedCategories()`.
    6. Filters and executes only supported `SyntaxChecker` instances across tokens and AST.
    7. Merges internal diagnostics and canonical checker diagnostics applying **ADR 0015**.
    8. Calls `DiagnosticAggregator` to sort diagnostics and compute counts.
    9. Returns immutable `AnalysisResult(status, tokens, astRoot, diagnostics, summary)`.
  * **Fault Containment**: Wraps each checker execution in defensive try-catch blocks. If a checker crashes unexpectedly, it generates an `ERR_INTERNAL_CHECK_FAILURE` diagnostic rather than crashing the pipeline.

### 3.3. Diagnostic Deduplication & Supersession (`docs/adr/0015-...`)
* [`0015-cross-stage-diagnostic-deduplication-and-canonical-supersession.md`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/docs/adr/0015-cross-stage-diagnostic-deduplication-and-canonical-supersession.md):
  * **The Problem**: If a line is missing a semicolon, both the parser (internal) and the statement checker (canonical) flag it. Emitting both creates confusing duplicates in the UI.
  * **The Rule**: When an internal diagnostic and a canonical diagnostic share the same `CheckCategory` and `SourceLocation` (line, column):
    * **Canonical supersedes Internal**.
    * The internal diagnostic is dropped; the canonical diagnostic—with its standardized error code (e.g. `ERR_MISSING_SEMICOLON`) and actionable `suggestedFix` (e.g. *"Insert missing ';'"*)—is retained.
* [`DiagnosticAggregator.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/analysis/DiagnosticAggregator.java):
  * Sorts diagnostics by line, then column.
  * Calculates `DiagnosticSummary` counts: total errors, warnings, and info items.

---

## 4. Architectural Deep Dive: ADR 0015 Canonical Supersession

Consider code missing a semicolon:
```java
int count = 42
```
1. `JavaParser` detects the missing token and generates an internal diagnostic:
   * Code: `JAVA_MISSING_SEMICOLON`
   * Category: `STATEMENT_TERMINATOR`
   * Message: `"Syntax error on token, ';' expected"`
   * Suggested Fix: *null*
2. `StatementTerminatorAndBlockChecker` inspects the token stream and generates a canonical diagnostic:
   * Code: `ERR_MISSING_SEMICOLON`
   * Category: `STATEMENT_TERMINATOR`
   * Message: `"Missing statement terminator ';'"`
   * Suggested Fix: `"Insert ';' at end of line"`

If both are displayed, the user sees redundant errors. 
**Under ADR 0015**:
`AnalysisOrchestrator` groups diagnostics by `(CheckCategory, line, column)`. Because the canonical diagnostic has richer semantics and an actionable repair suggestion, it cleanly supersedes the raw internal parser diagnostic!

---

## 5. Spoken Presentation Script

### Slide 10: Phase 3 — Universal Rule Checking Suite (0:00 – 1:30)
> *"Thank you, Member B. I am Member C, and I will be discussing our Universal Rule Checking Suite, our Analysis Orchestration engine, and our diagnostic deduplication policy.*
>
> *Looking at Slide 10, in addition to parser error detection, Writely runs six modular `SyntaxChecker` implementations:
> 1. `DelimiterMatchingChecker` validates balanced parentheses, brackets, and braces using a stack algorithm.
> 2. `StringAndCharacterLiteralChecker` checks for unterminated quotes and illegal escape characters.
> 3. `StatementTerminatorAndBlockChecker` enforces statement terminators and block closures.
> 4. `OperatorSyntaxChecker` catches adjacent binary operators like `a ++* b`.
> 5. `ControlStructureHeaderChecker` ensures conditionals in `if`, `for`, and `while` loops are properly formed.
> 6. `IdentifierSyntaxChecker` flags naming violations and invalid variable start characters.*
>
> *Why have separate syntax checkers when the parser already parses? Two key reasons:
> First, universal rules like delimiter balance work identically across languages, so we don't duplicate logic.
> Second, universal checkers provide actionable repair suggestions—they don't just say 'syntax error', they suggest 'Insert missing semicolon at line 4'."*

### Slide 11: Phase 4 — Analysis Orchestrator Lifecycle (1:30 – 2:50)
> *"Now follow the sequence diagram on Slide 11. This illustrates the complete lifecycle coordinated by `AnalysisOrchestrator`.*
>
> *Step 1 to 3: The UI passes a `SourcePayload` to the Orchestrator, which resolves the appropriate `ParserAdapter` from the registry.*
>
> *Step 4 to 7: The Orchestrator invokes `adapter.tokenize()` and `adapter.parse()`, receiving a `ParseResult` with the AST and internal parser diagnostics.*
>
> *Step 8 and 9: The Orchestrator inspects the adapter's `RuleCapabilities`. It filters our six checkers, executing only those supported by the language. For example, Python opts out of the statement terminator checker, so it is never run on Python code.*
>
> *Notice our fault containment: each checker runs inside a defensive try-catch guard. Even if a third-party rule checker were to throw an unhandled exception, the Orchestrator catches it, creates an internal failure diagnostic, and finishes the pipeline without crashing the application.*
>
> *Steps 10 to 13: Diagnostics are merged, aggregated into counts, packaged into an immutable `AnalysisResult`, and returned to the UI."*

### Slide 12: Phase 4 — ADR 0015 Canonical Supersession (2:50 – 4:10)
> *"Slide 12 highlights one of the most critical architectural designs in Writely: ADR 0015, Canonical Supersession.*
>
> *Consider what happens when a user forgets a semicolon in Java. Both `JavaParser` and `StatementTerminatorAndBlockChecker` detect the error. If we emit both, the user gets two noisy, redundant error cards for the same missing character.*
>
> *Under ADR 0015, the Orchestrator compares internal parser diagnostics against canonical checker diagnostics. If they share the same `CheckCategory` and the same line and column, the Canonical diagnostic supersedes the Internal diagnostic!*
>
> *The internal diagnostic is discarded, and the canonical diagnostic—with its clean error code and actionable `suggestedFix`—is presented to the user. This guarantees a clean, 100% deduplicated list of issues.*
>
> *Now that our `AnalysisResult` is compiled and deduplicated, how do we render it smoothly in the desktop interface without stuttering? I'll hand over to Member D for GUI Decoupling and our architectural summary."*

---

## 6. Handoff Cue & Anticipated Q&A
* **Handoff Cue**: *"With our rules validated, diagnostics deduplicated under ADR 0015, and the `AnalysisResult` compiled, I will pass the floor to Member D to walk through GUI Decoupling and our final Architectural Tradeoffs."*
* **Likely Question**: *"What happens if a canonical checker misses an error that the parser caught?"*
  * **Answer**: *"That's the beauty of ADR 0015! It only supersedes when both diagnostics match the exact same category and location. If the parser catches a rare syntax error that our canonical checkers don't cover, the parser's internal diagnostic is preserved and displayed to the user. We get the safety of the compiler parser combined with the clean guidance of our canonical checkers."*
