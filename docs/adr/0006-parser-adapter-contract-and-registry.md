# 6. Parser Adapter Contract and Registry Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

The Multi-Input Syntactical Analyzer (`writely.syntax_analyzer`) supports static syntax analysis across Java, Python, and C++. To keep the application core clean, language-specific syntax rules, tokenization routines, and AST generation logic must remain decoupled from core orchestration and GUI layers.

The system requires an extensible parser adapter contract and registry to:
1. Identify target languages and supported file extensions.
2. Provide uniform entry points for lexical tokenization and syntactical parsing.
3. Expose language-specific rule capabilities (keywords, operators, control headers, statement termination, indentation blocks).
4. Allow explicit user selection of programming language while supporting automatic file extension inference when explicit selection is omitted.
5. Return common parse results and diagnostics to the core pipeline while preserving native syntax-tree structures.

## Decision

We defined the parser adapter contract and registry under `com.writely.syntax_analyzer.adapter`:

1. **Parser Adapter Contract (`ParserAdapter`)**:
   - `Language language()`: Exposes target language enum (`JAVA`, `PYTHON`, `CPP`).
   - `Set<String> supportedExtensions()`: Supported extensions with default implementation delegating to `Language.extensions()`.
   - `boolean supportsExtension(String extension)`: Case-insensitive extension matching supporting forms with or without leading dot.
   - `RuleCapabilities ruleCapabilities()`: Returns language-specific syntactical capabilities.
   - `List<Token> tokenize(SourcePayload payload)`: Lexical entry point delegating to `Tokenizer.forLanguage(language())`.
   - `ParseResult parse(SourcePayload payload)`: Unified parsing entry point tokenizing and parsing into common result.
   - `ParseResult parse(SourcePayload payload, List<Token> tokens)`: Pre-scanned token parsing entry point.

2. **Common Parse Result (`ParseResult`)**:
   - Immutable record carrying `Optional<SyntaxNode> syntaxTree`, `List<Diagnostic> diagnostics`, `Optional<Object> nativeSyntaxTree`, and `List<Token> tokens`.
   - Preserves native syntax tree structures (`Optional<Object> nativeSyntaxTree`) for language-native inspection while providing uniform domain `SyntaxNode` ASTs.
   - Diagnostic status helpers (`hasErrors()`, `isSuccessful()`, `errorCount()`, `warningCount()`).

3. **Language Rule Capabilities (`RuleCapabilities`)**:
   - Immutable record specifying supported check categories (`Set<CheckCategory>`), statement terminator requirements, significant indentation block support, language keywords, operators, and control structure keywords.
   - Pre-configured language presets: `forJava()`, `forPython()`, and `forCpp()`, as well as a flexible `Builder`.

4. **Language Adapters (`JavaAdapter`, `PythonAdapter`, `CppAdapter`)**:
   - Concrete implementations exposing language identity, extensions, and rule capabilities.
   - Provide non-blocking stubs emitting root compilation/module/translation AST nodes and empty diagnostics, with pluggable delegation for full AST parsing implemented in downstream issues (#4, #5, #18).

5. **Adapter Registry (`ParserAdapterRegistry`)**:
   - Thread-safe registry mapping `Language` to `ParserAdapter`.
   - Pre-populates Java, Python, and C++ adapters by default.
   - Provides explicit language selection resolution prioritizing user-specified language over file extension inference:
     `resolveAdapter(Language explicitLanguage, String fileNameOrPath)`.
   - When `explicitLanguage` is `null`, infers language from file extension or file name.
   - Throws clear exceptions when required adapters are missing.

## Consequences

- The core pipeline and GUI remain completely decoupled from language-specific parsing implementation details.
- Explicit language overrides prevent incorrect parsing when file extensions are misleading or non-standard.
- Native parser trees are safely preserved alongside the unified domain model.
- Downstream parser issues (#4 for Python, #5 for Java, #18 for C++) can plug directly into their respective adapters without altering core contracts.
