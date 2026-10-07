# 1. Java Core Foundation and Desktop Toolkit Selection

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

The Multi-Input Syntactical Analyzer (`writely.syntax_analyzer`) requires a robust, platform-agnostic application core capable of executing static syntax parsing across Java, Python, and C++ source code. The project requires desktop delivery with interactive GUI inspection (editor, syntax tree, token views, diagnostic reports) and local-first offline execution.

## Decision

1. **Language & Runtime**: Java 21 LTS (OpenJDK 21).
2. **Build System**: Gradle with Gradle Wrapper (`gradlew`).
3. **UI Toolkit**: JavaFX (OpenJFX 21) configured via the `org.openjfx.javafxplugin`.
4. **Package Root**: `com.writely.syntax_analyzer`
5. **Layered Module Layout**:
   - `com.writely.syntax_analyzer.app`: Application bootstrap and lifecycle.
   - `com.writely.syntax_analyzer.core`: Pipeline orchestration and execution engine.
   - `com.writely.syntax_analyzer.domain`: Common AST, token, diagnostic, and result models.
   - `com.writely.syntax_analyzer.adapter`: Parser adapter interface contracts and registry.
   - `com.writely.syntax_analyzer.gui`: JavaFX views, controllers, and visual components.

## Consequences

- The application core remains strictly decoupled from UI rendering concerns.
- JavaFX provides modern desktop styling and view hierarchies for tree/token inspection.
- Gradle wrapper enables reproducible CI/CLI builds across developer environments.
