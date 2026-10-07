# 11. C++ Parser Adapter Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

Static syntactical analysis within `writely.syntax_analyzer` requires robust parsing of C++ translation units, code snippets, and single-line statements into structured syntax trees alongside actionable diagnostic records. Under ADR 0006, all language-specific parsers conform to the universal `ParserAdapter` contract and register with `ParserAdapterRegistry`.

C++ presents unique lexical and syntactical challenges:
1. **Grammar Complexity**: Rich declaration syntax (qualifiers, pointer/reference modifiers, operator overloads, constructor initializers), template declarations and specializations, and distinct control-flow constructs.
2. **Preprocessing and Macros**: The presence of preprocessor directives (`#include`, `#define`, `#pragma`) that normally precede or interleave code.
3. **Absence of External Toolchain**: The analyzer runs in a lightweight, pure-Java environment and must never invoke external compilers (`clang`, `gcc`, `cl.exe`) or execute analyzed code.
4. **Resilient Error Recovery**: Malformed C++ code (missing semicolons, unmatched delimiters, malformed headers) must not crash the parser or cascade into false-positive failures across subsequent declarations.

## Decisions

1. **Parser Adapter Implementation**:
   Implement `CppAdapter` implementing `ParserAdapter` under `com.writely.syntax_analyzer.adapter`, delegating default parsing to `CppParser` in `com.writely.syntax_analyzer.adapter.cpp`. Retain constructor support for injectable `customParser` delegates for mockability and backward compatibility.

2. **C++ Baseline Subset Coverage**:
   Target a practical core baseline encompassing modern C++ (C++17/C++20 baseline):
   - **Includes and Directives**: Recognizes system (`#include <header>`) and local (`#include "header"`) includes as dedicated `IncludeDirective` nodes.
   - **Namespaces**: Named, nested (`namespace a::b`), and anonymous namespaces, plus `using namespace` directives and `using Alias = Type` type aliases.
   - **Classes and Structs**: `class` and `struct` declarations, single/multiple inheritance, access specifiers (`public:`, `protected:`, `private:`), constructors with member initializer lists, destructors (`~ClassName()`), member variables, and member functions.
   - **Functions**: Prototypes and definitions, parameter lists with default values, return types, qualifiers (`const`, `noexcept`, `override`, `final`), and pure virtual specifiers (`= 0`).
   - **Variable Declarations**: Primitives, pointers (`*`, `**`), references (`&`), qualifiers (`const`, `static`, `constexpr`, `auto`), multi-variable declarations (`int a = 1, b = 2;`), and uniform initializers.
   - **Conditionals**: `if`/`else if`/`else`, `switch` statements with `case` and `default` branches.
   - **Loops**: Classic `for (init; cond; step)`, range-based `for (auto& x : items)`, `while (cond)`, and `do ... while (cond);`.
   - **Expressions**: Precedence climbing / Pratt parser supporting binary arithmetic, comparison (`<=>`, `==`, `!=`, `<`, `>`, `<=`, `>=`), bitwise, logical, shift (`<<`, `>>`), assignment, ternary (`? :`), unary prefix (`++`, `--`, `+`, `-`, `!`, `~`, `*`, `&`), postfix (`()`, `[]`, `.`, `->`, `++`, `--`), scope resolution (`::`), and literal expressions.
   - **Basic Templates**: `template <typename T, class U = int>` prefixing classes, structs, and functions.

3. **Preprocessor and Macro Boundaries**:
   - The parser performs static syntactical analysis directly on scanned token streams without running an external preprocessor or executing macro expansion.
   - `#include` directives are ingested as structured `IncludeDirective` nodes with metadata (`header`, `type`).
   - Other preprocessor directives (`#define`, `#ifdef`, `#pragma`) are preserved as `PreprocessorDirective` AST nodes to maintain source structural integrity without code execution or file system header loading.

4. **AST Mapping and Native Tree Preservation**:
   - The parser constructs a strongly-typed native AST rooted at `CppTranslationUnit` consisting of immutable records (`CppClass`, `CppFunction`, `CppVariable`, `CppStatement`, `CppExpr`, etc.).
   - The native AST is exposed via `ParseResult.nativeSyntaxTree` as `CppTranslationUnit`.
   - Each native node implements `CppAstNode.toSyntaxNode()`, converting into the universal `SyntaxNode` tree rooted at `TranslationUnit` with accurate 1-based `SourceSpan`s, human-readable labels, and semantic attributes.

5. **Diagnostic Codes and Non-Cascading Recovery**:
   - Standard diagnostic codes emitted on syntax errors:
     - `CPP_MISSING_SEMICOLON` (`STATEMENT_TERMINATOR`): Missing semicolons after statements, declarations, and class definitions.
     - `CPP_UNCLOSED_DELIMITER` (`DELIMITER_MATCH`): Missing closing `)`, `}`, `]`, or `>`.
     - `CPP_CONTROL_HEADER` (`CONTROL_HEADER`): Malformed loop or conditional headers (missing `(` or condition).
     - `CPP_UNEXPECTED_TOKEN` (`OPERATOR_SYNTAX` / `STATEMENT_TERMINATOR`): Unrecognized tokens in expression or declaration contexts.
   - Synchronization routines skip tokens until reaching `;`, `}`, or top-level declaration keywords, allowing subsequent declarations to be parsed cleanly without cascading false errors.
   - Diagnostics report exact 1-based source coordinates (`line`, `column`) and provide actionable `suggestedFix` suggestions.

## Consequences

- Full, self-contained C++ parsing capability implemented natively in Java 21 LTS with zero external C++ toolchain dependencies.
- Plugs seamlessly into `ParserAdapterRegistry` and supports both explicit selection (`Language.CPP`) and extension inference (`.cpp`, `.hpp`, `.cxx`, `.cc`, `.h`).
- Preserves native typed C++ AST models for language-specific analysis while delivering uniform `SyntaxNode` trees to core reporting and GUI visualization.
- Resilient error handling and precise diagnostic reporting enables clear user feedback on incomplete or malformed C++ code.
