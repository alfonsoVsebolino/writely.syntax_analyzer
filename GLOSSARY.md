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
