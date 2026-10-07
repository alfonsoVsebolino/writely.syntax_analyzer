# 10. Python Parser Adapter Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

The Multi-Input Syntactical Analyzer (`writely.syntax_analyzer`) requires language-specific parser adapters to parse ingested source payloads, construct abstract syntax trees, and detect syntax errors. For Python source code, the system requires an adapter conforming to `ParserAdapter` that plugs into `ParserAdapterRegistry` and supports the documented Python 3 syntax baseline.

Python syntax exhibits unique syntactical characteristics that differentiate it from Java and C++:
1. **Significant Indentation & Block Structure**: Blocks are demarcated by `INDENT` and `DEDENT` tokens rather than curly braces `{}`. Improper indentation or missing indentation after compound headers represents a structural syntax violation.
2. **Colon-Required Compound Headers**: Compound statements (`def`, `class`, `if`, `elif`, `else`, `for`, `while`, `try`, `except`, `finally`, `with`, `match`, `case`) mandate a trailing colon `:` before beginning their statement suite.
3. **Diverse String Forms & Literal Expressions**: Python 3 supports single-, double-, and triple-quoted strings, string prefixes (`f`, `r`, `b`, `u`), walrus operators (`:=`), list/dict/set comprehensions, slicing, and lambda expressions.
4. **Distinction Between Syntax and Semantic Errors**: Semantic name resolution failures (such as referencing an undeclared variable) or type mismatches are runtime concerns and must not be classified as syntax errors.

## Decisions

1. **Adapter Architecture and Registry Integration**:
   - `PythonAdapter` implements `ParserAdapter` under `com.writely.syntax_analyzer.adapter`, registering with `Language.PYTHON`, supported extension `.py`, and `RuleCapabilities.forPython()`.
   - Automatically pre-registered in `ParserAdapterRegistry`, supporting both explicit language selection and file-extension inference.
   - Implements `ParseResult parse(SourcePayload payload, List<Token> tokens)` by delegating to a recursive descent parser `PythonParser` under `com.writely.syntax_analyzer.adapter.python`.

2. **Python Grammar Baseline Coverage**:
   - **Imports**: `import a`, `import a, b as c`, `from a import b as c`, relative imports (`from . import x`, `from ..parent import y`), and wildcard imports (`from m import *`).
   - **Assignments**: Simple assignments (`x = 1`), tuple/multiple targets (`a, b = 1, 2`), annotated assignments (`x: int = 1`), and augmented assignments (`+=`, `-=`, `*=`, `/=`, `//=`, `%=`, `**=`, `&=`, `|=`, `^=`, `<<=`, `>>=`).
   - **Functions (`def`)**: Standard functions, parameter lists with type annotations, default argument values, variadic positional arguments (`*args`), keyword-only arguments, keyword variadics (`**kwargs`), and return type annotations (`->`).
   - **Classes (`class`)**: Class definitions with single or multiple base classes and metaclass arguments.
   - **Decorators**: Decorator expressions (`@decorator`, `@decorator(args)`) preceding `def` or `class` definitions.
   - **Conditionals & Pattern Matching**: `if`, `elif`, and `else` branches; `match` and `case` pattern matching clauses.
   - **Loops**: `for ... in ... [else ...]` and `while ... [else ...]`, supporting `break` and `continue`.
   - **Exception Handling & Context Managers**: `try`, `except [as]`, `else`, `finally`, and `with [as]` context manager statements.
   - **Simple Statements**: `return`, `raise`, `assert`, `pass`, `global`, `nonlocal`, `del`, `yield`, and `yield from`.
   - **Expressions**: Binary and unary operators, ternary conditional expressions (`x if c else y`), walrus operator (`:=`), lambda expressions, list/dict/set comprehensions, generator expressions, subscription/slicing (`a[1:5:2]`), and call expressions.
   - **String Forms**: Single/double/triple-quoted strings and prefixes (`f`, `r`, `b`, `u`).

3. **Indentation Block Handling**:
   - Leverages `PythonTokenizer`'s indentation stack emissions (`INDENT`, `DEDENT`, `NEWLINE`).
   - Suites following a colon `:` can be either an indented block (`NEWLINE` + `INDENT` ... `DEDENT`) or a single-line simple statement.
   - When a colon header is followed by a newline but lacks an `INDENT` token, `ERR_EXPECTED_INDENTED_BLOCK` is emitted under `CheckCategory.STATEMENT_TERMINATOR`.
   - When an unexpected `INDENT` token appears at module level or out of place, `ERR_UNEXPECTED_INDENT` is emitted under `CheckCategory.STATEMENT_TERMINATOR`.

4. **Header Validation**:
   - Validates that compound statement headers (`def`, `class`, `if`, `elif`, `else`, `for`, `while`, `try`, `except`, `finally`, `with`, `match`, `case`) terminate with `:`.
   - If `:` is omitted, `ERR_MISSING_COLON` is emitted under `CheckCategory.CONTROL_HEADER` at the exact 1-based source coordinate following the header clause.
   - Malformed header structures (missing conditions in `if`/`while`, missing loop target or `in` keyword in `for`, missing function/class identifiers) emit `ERR_MALFORMED_HEADER` under `CheckCategory.CONTROL_HEADER`.

5. **AST Mapping and Native Tree Preservation**:
   - Native Python AST records (`PyModule`, `PyFunctionDef`, `PyClassDef`, `PyIf`, etc.) are defined under `com.writely.syntax_analyzer.adapter.python.ast`.
   - `PyNode.toSyntaxNode()` maps the native tree to domain `SyntaxNode` ASTs, preserving hierarchical structure, source spans, and metadata attributes.
   - `ParseResult` encapsulates both domain `SyntaxNode` (`syntaxTree()`) and the native `PyModule` (`nativeSyntaxTree()`), allowing native AST queries via `result.nativeSyntaxTree(PyModule.class)`.

6. **Semantic Error Exclusion**:
   - The parser strictly enforces syntax legality.
   - Name resolution errors (e.g. referencing undeclared variables), type compatibility, and built-in name shadowing are not classified as syntax errors and produce zero diagnostics.

## Consequences

- Full compliance with Python 3 baseline syntax, indentation semantics, and header validation requirements.
- Standardized diagnostics with 1-based source coordinates, actionable messages, and suggested fixes for IDE feedback.
- Native AST tree preserved alongside domain `SyntaxNode` representations.
- Zero breaking changes to `ParserAdapterRegistry` or downstream analysis checks.
