# 9. Java Parser Adapter Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

Static syntactical analysis within `writely.syntax_analyzer` requires robust parsing of Java compilation units, multi-line code snippets, and single-line programming commands into hierarchical syntax trees accompanied by accurate diagnostic findings. Under ADR 0006, all language-specific parsers conform to the universal `ParserAdapter` contract and register with `ParserAdapterRegistry`.

Java presents specific syntactical characteristics that must be supported:
1. **Modern Language Baseline**: Java 21 LTS syntax, including records, sealed classes, pattern matching `instanceof`, switch expressions with rule arrows (`->`), compact constructors, and lambda expressions.
2. **Multi-Modal Ingestion**: Ingestion spans full program files (packages, imports, classes), multi-line snippets (isolated methods or control structures), and single statement commands (e.g. `x = 10;` or `System.out.println("Hello");`).
3. **Pure-Java Environment & Zero Execution**: The analyzer operates in a lightweight, pure-Java environment and must never invoke external compilers or execute analyzed code. Furthermore, semantic type resolution and classpath symbol resolution are strictly out of scope for static syntactical analysis.
4. **Resilient Error Recovery**: Syntax defects (missing semicolons, unmatched parentheses or braces, malformed control headers, invalid operator sequences) must be detected with pinpoint 1-based source coordinates and must not cascade into false positive errors across subsequent declarations.
5. **Dual AST Representation**: The system requires exposing both a language-specific typed AST (for Java-native static analysis) and the universal `SyntaxNode` tree (for cross-language UI visualization and reporting).

## Decisions

1. **Parser Adapter Implementation**:
   Implement `JavaAdapter` implementing `ParserAdapter` under `com.writely.syntax_analyzer.adapter`, delegating default parsing to `JavaParser` in `com.writely.syntax_analyzer.adapter.java`. Retain constructor support for injectable `customParser` delegates for mockability and backward compatibility.

2. **Java Baseline Grammar Subset Coverage**:
   Target a comprehensive baseline encompassing Java 21 LTS language constructs:
   - **Packages and Imports**: Package declarations (`package a.b.c;`), single-type imports, wildcard on-demand imports (`import java.util.*;`), and static imports (`import static java.lang.Math.PI;`).
   - **Type Declarations**: Classes (`class`), interfaces (`interface`), records (`record`), and enumerations (`enum`). Support modifiers (`public`, `protected`, `private`, `static`, `final`, `abstract`, `sealed`, `non-sealed`), type parameter bounds (`<T extends Comparable<T>>`), `extends`, `implements`, and `permits` clauses.
   - **Members**: Fields (including multiple declarators and array suffixes), methods (return types, parameter lists, varargs `...`, `throws` clauses, and abstract or default bodies), constructors (canonical, overloaded, and compact record constructors), and initializer blocks (`static { ... }`, `{ ... }`).
   - **Conditionals & Switches**: `if`/`else`, `switch` statements, and modern `switch` rule entries (`case ->`) and expressions.
   - **Loops**: Classic `for (init; cond; update)`, enhanced for-each `for (Type var : iterable)`, `while (cond)`, and `do ... while (cond);`.
   - **Exception Handling**: `try-with-resources` (supporting both semicolon-separated and single resource declarations), multiple `catch` blocks, and optional `finally` blocks.
   - **Transfer Statements**: `return`, `throw`, `break [label]`, `continue [label]`, `yield`, and `assert [expr : detail]`.
   - **Expressions**: Precedence climbing / Pratt parser supporting binary arithmetic, comparison, logical, bitwise, shift, assignment, ternary (`? :`), unary prefix/postfix (`++`, `--`, `+`, `-`, `!`, `~`), casts (`(Type) expr`), pattern `instanceof`, object instantiation (`new Type(...)`), array creation (`new int[10]`, `new int[] {1, 2}`), method invocations, member dereferencing (`.`), method references (`::`), lambdas (`->`), `this`, and `super`.

3. **Multi-Modal Parsing Strategy**:
   - The parser constructs a root `JavaCompilationUnit` node representing the file, snippet, or command.
   - When encountering source input that begins directly with statements or expressions (Input Mode 1 and Input Mode 2), the parser parses them into top-level declarations within the `JavaCompilationUnit` without requiring artificial boilerplate class wrappers.

4. **AST Mapping and Native Tree Preservation**:
   - Strongly-typed native AST nodes reside under `com.writely.syntax_analyzer.adapter.java.ast`: `JavaCompilationUnit`, `JavaClassDeclaration`, `JavaMethodDeclaration`, `JavaStatement`, `JavaExpression`, etc., implemented as immutable Java records with exact `SourceSpan` boundaries.
   - `ParseResult.nativeSyntaxTree` preserves the `JavaCompilationUnit` instance for type-safe retrieval via `result.nativeSyntaxTree(JavaCompilationUnit.class)`.
   - `JavaAstMapper` transforms the native tree into the universal `SyntaxNode` tree rooted at `"CompilationUnit"`, establishing consistent kind identifiers, readable labels, accurate source spans, and semantic attribute maps.

5. **Operator and Generics Disambiguation**:
   - `JavaTokenStream` encapsulates token traversal with on-demand decomposition of `>>` and `>>>` tokens in generic type contexts (e.g. `Comparable<T>>`). This resolves nested type argument brackets cleanly without disrupting the right-shift operators in numeric expressions.
   - Distinct lookahead heuristics disambiguate local variable declarations (`Type var = init;`) from expression statements (`expr;`), and cast expressions (`(Type) expr`) from parenthesized expressions (`(expr)`) and lambdas (`(a, b) -> expr`).

6. **Diagnostic Categorization and Error Recovery**:
   - Emits structured `Diagnostic` instances categorized according to `CheckCategory`:
     - `STATEMENT_TERMINATOR` (`ERR_JAVA_MISSING_SEMICOLON`): Flagged at the previous token's end location when a statement terminator `;` is omitted.
     - `DELIMITER_MATCH` (`ERR_JAVA_UNCLOSED_PARENTHESIS`, `ERR_JAVA_UNCLOSED_BRACE`, `ERR_JAVA_UNCLOSED_BRACKET`): Flagged when matching delimiter boundaries are missing.
     - `CONTROL_HEADER` (`ERR_JAVA_INVALID_CONTROL_HEADER`): Flagged when control flow headers (`if`, `while`, `for`, `switch`) omit required parentheses.
     - `OPERATOR_SYNTAX` (`ERR_JAVA_OPERATOR_SYNTAX`, `ERR_JAVA_UNEXPECTED_TOKEN`): Flagged on invalid consecutive operators or missing operands.
     - `IDENTIFIER_NAMING` (`ERR_JAVA_EXPECTED_IDENTIFIER`): Flagged when identifiers are expected but missing in declarations.
   - Non-cascading panic-mode recovery synchronizes on statement boundaries (`;`, `}`) and declaration starter keywords (`public`, `class`, `if`, `return`), allowing subsequent constructs to parse successfully.

7. **Strict Static Analysis Boundary**:
   - The adapter performs purely structural and syntactic validation.
   - Zero semantic type resolution, zero reflection on analyzed classes, and zero code execution are performed, ensuring security, safety, and instant parsing performance.

## Consequences

- Full, self-contained Java 21 parser implemented in pure Java with zero external dependencies.
- Plugs cleanly into `ParserAdapterRegistry` and fully satisfies the `ParserAdapter` contract.
- Unifies single command, multi-line snippet, and full compilation unit handling into a consistent AST model.
- Preserves native typed AST structures for specialized language inspection while supplying uniform domain `SyntaxNode` trees for GUI and reporting pipelines.
- Generates pinpoint diagnostics with 1-based source coordinates and actionable repair suggestions.
