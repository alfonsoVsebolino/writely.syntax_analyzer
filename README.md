# writely.syntax_analyzer

> A robust, multi-modal static syntactical analyzer and code inspection desktop application built with Java 21 and JavaFX 21.

---

## Overview

**writely.syntax_analyzer** is an offline static analysis tool designed for parsing, tokenizing, and evaluating source code against strict syntactical rules across multiple programming languages. It provides real-time lexical inspection, AST (Abstract Syntax Tree) visualization, categorical syntax diagnostics, and exportable analysis reports in both formatted text and structured JSON.

---

## Key Features

- **Multi-Modal Source Ingestion**:
  - **Single Line**: Validate standalone statements and expressions.
  - **Code Snippet**: Multi-line scratchpad with real-time gutter line numbering.
  - **File Ingestion**: Open local files from disk with automated language detection.
- **Comprehensive Syntax Checking (6 Canonical Categories)**:
  1. `DELIMITER_MATCH`: Parentheses `()`, brackets `[]`, and braces `{}` balancing.
  2. `LITERAL_SYNTAX`: Unterminated strings, invalid escape sequences, and character literals.
  3. `STATEMENT_TERMINATOR`: Missing semicolons `;` (Java/C++) or improper indentation and colons `:` (Python).
  4. `OPERATOR_SYNTAX`: Consecutive illegal operators, missing operands, and unary/binary errors.
  5. `CONTROL_HEADER`: Structural validation of `if`, `for`, `while`, and `switch` headers.
  6. `IDENTIFIER_NAMING`: Illegal identifier names and character violations.
- **Deep Inspection Views**:
  - **Diagnostics List**: Interactive diagnostic cards with severity badges, error codes, and click-to-highlight editor navigation.
  - **Tokens Table**: Filterable token stream showing lexemes, token types, channel (trivia vs. default), and line/column coordinates.
  - **AST Hierarchy**: Interactive collapsible/expandable syntax tree representation.
- **Report Export**: Save audit reports in formatted Text (`.txt`) or structured JSON (`.json`).

---

## Desktop Packaging & Launch Instructions

### Prerequisites
- Java Development Kit (JDK) 21 or higher installed (`java --version`).

### Building the Desktop Distribution
To package a standalone executable desktop distribution using the Gradle `application` plugin:

```bash
./gradlew installDist
```

This compiles the project, bundles dependencies, and builds an unpackaged executable distribution under:
`build/install/syntax_analyzer/`

### Launching the Standalone Application

- **Linux / macOS**:
  ```bash
  ./build/install/syntax_analyzer/bin/syntax_analyzer
  ```

- **Windows (Command Prompt / PowerShell)**:
  ```cmd
  build\install\syntax_analyzer\bin\syntax_analyzer.bat
  ```

### Packaging Archive Files
To create portable ZIP or TAR archives for distribution:

```bash
# Creates build/distributions/syntax_analyzer-0.1.0-SNAPSHOT.zip
./gradlew distZip

# Creates build/distributions/syntax_analyzer-0.1.0-SNAPSHOT.tar
./gradlew distTar
```

### Development Run
To run directly from source during development:

```bash
./gradlew run
```

### Running Tests
To run the automated test suite:

```bash
./gradlew test
```

---

## Strict Offline & Static Safety Guarantee

The analyzer is built with strict offline and static safety guarantees suitable for secure code evaluation and isolated environments:

1. **100% Offline Execution**:
   - **Zero Network Sockets**: The application opens zero network sockets and makes zero external HTTP, HTTPS, or WebSocket connections.
   - **Zero Telemetry / Analytics**: No crash reporting, telemetry pings, background check-ins, or cloud dependencies exist in the codebase.
   - **Air-Gapped Ready**: Operates completely self-contained without internet connectivity.

2. **Zero Bytecode Execution**:
   - Analyzed code is parsed strictly as passive textual data.
   - Source code is **NEVER** compiled into bytecode, interpreted, evaluated via `eval`, or dynamically executed in any runtime or child process.
   - Untrusted source inputs cannot execute arbitrary code or trigger OS-level commands.

3. **Input Hardening & Malformed Input Boundaries**:
   - **10 MB File Size Ceiling**: Enforces an explicit 10 MB limit (`10 * 1024 * 1024` bytes) in `SourceIngestionService` to guard against memory exhaustion and OutOfMemory errors.
   - **Binary File Detection Guard**: Scans the first 8 KB of any uploaded file for null bytes (`0x00`) and validates UTF-8 character encoding. Files with binary content or non-UTF-8 encodings are immediately rejected with an `IngestionException`.
   - **GUI Error Boundary**: Any ingestion failure, parser error, or unexpected I/O exception is safely caught by the UI layer, updates the status bar, and displays a non-blocking error alert (`Alert(AlertType.ERROR)`) without terminating or crashing the JavaFX application.

---

## Supported Language Baselines

The analyzer supports three primary programming language syntaxes:

| Language | Syntax Baseline | Supported Constructs |
| :--- | :--- | :--- |
| **Python** | **Python 3.8+** | Indentation blocks, colons, multi-line strings, f-strings, control structures (`if/elif/else`, `while`, `for/in`), function definitions, match/case syntax. |
| **Java** | **Java 17 – 21** | Modern Java grammar, classes, records, interfaces, text blocks, switch expressions, pattern matching, try-with-resources, annotations. |
| **C++** | **C++17 / C++20** | Modern C++ grammar, functions, structs/classes, namespaces, templates, lambdas, structured bindings, modern control statements. |

---

## C++ Preprocessor Limitations

When analyzing C++ source files, the analyzer operates under the following static model:

- **No Macro Expansion**: Preprocessor macros (such as `#define MACRO ...`) are **not** expanded or substituted into the source stream. Code using complex token-pasting (`##`) or stringification (`#`) is parsed literally.
- **Preprocessor Directives as Trivia**: Directives such as `#include`, `#define`, `#ifdef`, `#ifndef`, `#endif`, `#pragma`, and `#undef` are parsed as directive trivia and statement headers rather than evaluated via a multi-pass preprocessor.
- **No External Header Resolution**: `#include` statements do not open or resolve external system headers (e.g. `<iostream>`, `<vector>`). Source files and headers (`.cpp`, `.hpp`, `.h`, `.cc`, `.cxx`) are inspected independently and self-containedly.

---

## Project Structure

```
├── build.gradle.kts           # Build configuration and application packaging
├── settings.gradle.kts        # Gradle settings
├── system_requirements.md     # Detailed course specifications and requirements
├── GLOSSARY.md                # Domain models and ubiquitous language definitions
├── src/
│   ├── main/
│   │   ├── java/com/writely/syntax_analyzer/
│   │   │   ├── app/           # JavaFX application shell, Launcher, WorkspaceController
│   │   │   ├── domain/        # Core domain records (Token, Diagnostic, AnalysisResult)
│   │   │   ├── core/
│   │   │   │   ├── analysis/  # AnalysisOrchestrator and DiagnosticAggregator
│   │   │   │   ├── check/     # Syntax checkers (Delimiters, Literals, Terminators, etc.)
│   │   │   │   ├── ingestion/ # SourceIngestionService and DefaultSourceIngestionService
│   │   │   │   ├── report/    # ReportExportService (Text and JSON exporters)
│   │   │   │   └── tokenization/ # Lexers and tokenizers for Java, Python, C++
│   │   │   └── adapter/       # Parser adapters for Java, Python, C++
│   │   └── resources/
│   │       ├── fxml/          # main-workspace.fxml layout
│   │       └── css/           # workspace.css stylesheet
│   └── test/                  # Comprehensive unit, integration, and UI test suites
```

---

## License

Academic and educational use project. See course evaluation criteria in `system_requirements.md`.
