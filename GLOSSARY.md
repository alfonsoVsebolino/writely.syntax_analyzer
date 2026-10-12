# Glossary: Multi-Input Syntactical Analyzer

Ubiquitous language and domain vocabulary across the core engine, GUI, and language adapters.

## Input & Ingestion
- **Input Mode**: One of three supported source ingestion mechanisms:
  1. `Single Line`: A single command or statement.
  2. `Code Snippet`: An arbitrary multi-line block of source code.
  3. `File Upload`: A program source file selected from the local file system.
- **Source Payload**: The raw character sequence ingested along with metadata (origin filename, character count, detected or specified language).
- **Source Location**: A 1-based coordinate (`line`, `column`) pinpointing characters and tokens in the source text.

## Analysis & Lexical Processing
- **Token**: The minimal lexical unit emitted by tokenization, containing `tokenType`, `lexeme`, `SourceLocation` span, and optional category.
- **Syntax Tree**: Hierarchical structural representation produced by a parser adapter representing statement/expression nodes.
- **Diagnostic**: An observation or error emitted during static analysis. Includes:
  - `severity`: `ERROR`, `WARNING`, or `INFO`.
  - `category`: Classification matching one of the required syntax check areas.
  - `location`: Exact `SourceLocation` (line and column).
  - `message`: Explanatory human-readable text.
- **Internal Parser Diagnostic**: A diagnostic emitted directly by a `ParserAdapter` during abstract syntax tree construction. It uses an internal language-specific code (for example, `ERR_JAVA_MISSING_SEMICOLON` or `CPP_UNCLOSED_DELIMITER`) to control parser error recovery.
- **Canonical Syntax Diagnostic**: A diagnostic emitted by a universal `SyntaxChecker`. It uses a standard cross-language code (for example, `ERR_MISSING_SEMICOLON` or `ERR_UNCLOSED_DELIMITER`) and provides an actionable repair suggestion. When an internal parser diagnostic and a canonical syntax diagnostic share the same `Check Category` and `Source Location`, the canonical diagnostic supersedes the internal diagnostic.
- **Check Category**: The standard syntactical inspection scopes:
  - `DELIMITER_MATCH`: Parentheses `()`, brackets `[]`, braces `{}` balancing.
  - `LITERAL_SYNTAX`: Unterminated or malformed string and character literals.
  - `STATEMENT_TERMINATOR`: Missing `;` (Java/C++) or improper indentation/colon `:` (Python).
  - `OPERATOR_SYNTAX`: Consecutive illegal operators, missing operands, invalid unary/binary usage.
  - `CONTROL_HEADER`: Structural validation of `if`, `for`, `while`, `switch` headers.
  - `IDENTIFIER_NAMING`: Illegal variable/function names or character violations.

## Architecture & Adapters
- **Parser Adapter**: A language-specific module (`JavaAdapter`, `PythonAdapter`, `CppAdapter`) conforming to the universal analyzer contract.
- **Analysis Result**: Aggregated summary model containing:
  - Ingestion metadata.
  - Emitted token stream.
  - Parsed syntax tree nodes.
  - Ordered list of `Diagnostic` findings.
  - Statistical summary (total lines, tokens, error count per category, overall status `PASSED`/`FAILED`).
- **Syntax Report**: Human-readable or JSON serialized output generated from an `AnalysisResult`.
- **Settings Service**: The application component that persists user preferences across launches. It is the sole mechanism for storing user settings; the Onboarding Guide's first-run state is its first stored preference.

## Interface & Guidance
- **Onboarding Guide**: The built-in walkthrough that explains the analyzer's scope, purpose, and usage. It is presented on first launch and can be reopened at any time from the workspace. It documents that analysis is strictly static and syntactical: analyzed code is never executed, and semantic or runtime failures (such as undefined names) are outside the analyzer's scope.
- **Guide Modal**: The application-modal overlay through which the Onboarding Guide is presented. It floats above the workspace with the workspace dimmed behind it to establish focus, closes via its close control, and can be reopened on demand.
