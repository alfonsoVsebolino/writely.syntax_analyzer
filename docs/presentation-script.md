# Writely Architecture Walkthrough — 4-Member Presentation Script

## Team Allocation Overview
- Member 1: System Foundations & Ingestion (Slides 1–4)
- Member 2: Frontend: Lexing & AST Parsing (Slides 5–8)
- Member 3: Universal Rules & Orchestration (Slides 9–12)
- Member 4: Reactive UI & Design Tradeoffs (Slides 13–15)

---

### Member 1: System Foundations & Ingestion (Slides 1 – 4)

#### Slide 1 — Title & Introduction
"Good day, everyone. Today, our team is presenting the architecture and code deconstruction pipeline of Writely—a multi-input, static syntactical analyzer engineered for Java, Python, and C++.

When most people think of compilers and syntax analyzers, they imagine black-box monolithic engines that are brittle and hard to extend. With Writely, our goal was the opposite: to build a modular, highly resilient static analysis engine around Clean Architecture principles. Over the next fifteen to twenty minutes, we will walk you through the entire journey—from raw code ingestion and tokenization to AST construction, universal rule checking, and non-blocking GUI rendering.

Let’s start by looking at how Writely establishes its macro boundaries."

#### Slide 2 — Architectural Boundaries & Module Seams
"Writely follows a strict Ports-and-Adapters, or hexagonal, architecture. As you can see, every dependency arrow points strictly inward toward our Domain layer.

1. At the core, the Domain module contains zero third-party dependencies—it is built purely from immutable records like Token, SyntaxNode, and Diagnostic.
2. The Adapter layer encapsulates all language-specific grammars for Java, Python, and C++.
3. The Core layer handles orchestration and universal syntax checking without knowing what language is being analyzed.
4. Finally, the App layer contains our JavaFX UI.

Because the delivery mechanism sits completely on the outer perimeter, if we decided tomorrow to replace JavaFX with a CLI or a web service, not a single line of code in Core, Adapter, or Domain would have to change."

#### Slide 3 — The Universal Seam: ParserAdapter
"Connecting the orchestrator to individual language implementations is Writely's central seam: the ParserAdapter interface.

Notice what is absent here: there are no 'if (language == JAVA)' branching statements anywhere in the engine. Instead, we use the Strategy pattern. Each adapter implements methods to tokenize, parse, and declare its RuleCapabilities.

RuleCapabilities is especially critical: it allows adapters to declaratively opt in or out of universal checks. For instance, the Python adapter simply opts out of semicolon termination checks. When we want to support a new language like Go or Rust, we only have to implement this single interface and register it in the ParserAdapterRegistry."

#### Slide 4 — Phase 1: Ingestion & Source Normalization
"Before any source code reaches a tokenizer, it passes through the SourceIngestionService.

Writely accepts code through three modalities: a single-line inspect prompt, an unsaved multiline scratchpad, or a full file upload. But raw input across different operating systems introduces subtle bugs—specifically CRLF versus LF line breaks. If you calculate token column offsets on Windows CRLF lines without normalization, character coordinates will drift, causing UI diagnostics to highlight the wrong tokens in the editor.

Ingestion sanitizes everything: it forces UTF-8 encoding, standardizes all line breaks to uniform Unix newlines (\n), and packages the data into an immutable SourcePayload. This guarantees deterministic mathematical coordinate indexing for all downstream stages.

Now, I’ll pass the floor to Member 2, who will take us through lexical analysis and AST construction."

---

### Member 2: Frontend: Lexing & AST Parsing (Slides 5 – 8)

#### Slide 5 — Phase 1: Lexical Analysis & Token Channels
"Thank you, Member 1. Once the source payload is normalized, the adapter's tokenizer breaks the raw character stream into discrete tokens.

Here, Writely adopts a pattern used by modern IDEs rather than classic batch compilers: a dual-channel token architecture.
- The Primary Channel carries significant syntax tokens—keywords, operators, literals, and identifiers—directly to the parser.
- The Trivia Channel preserves comments, indentation, and whitespace by tagging them with isTrivia() == true.

A batch compiler throws whitespace away immediately. But an interactive analyzer needs trivia so syntax highlighters can render comments correctly and rule checkers can catch issues like unterminated block comments or malformed docstrings without losing character coordinates."

#### Slide 6 — Multi-Language Tokenizer Contrast
"Because Writely supports three distinct language paradigms, each tokenizer must handle unique lexical semantics, as shown in this comparison:

- Java uses traditional curly braces and treats newlines as standard whitespace.
- Python, being indentation-sensitive, requires its lexer to maintain an internal integer stack. At the start of every line, it calculates indentation depth and synthesizes virtual INDENT and DEDENT tokens. It also treats newlines as significant statement terminators.
- C++ must detect preprocessor directives like #include and #define as distinct tokens so downstream checkers don't mistake the '#' symbol for an invalid operator.

Each tokenizer normalizes these quirks before handing the stream to the parser."

#### Slide 7 — Phase 2: Syntactic Parsing & AST Construction
"In Phase 2, the flat token stream is transformed into a hierarchical Abstract Syntax Tree.

A common flaw in basic parsers is that they crash or throw an unhandled exception on the first syntax error, discarding the entire tree. In Writely, our adapters practice fault-tolerant error recovery.

When an adapter encounters invalid syntax, it records an internal parser diagnostic, resynchronizes at the next statement delimiter—such as a semicolon or newline—and resumes parsing. As a result, ParseResult emits partial, valid AST subtrees along with any captured parser diagnostics. This guarantees the user still gets full visual feedback and code navigation for the rest of their file."

#### Slide 8 — AST Construction: JavaAstMapper Deep-Dive
"Looking under the hood of the Java adapter, the translation is executed by JavaAstMapper.

As you can see in the deconstruction example on the slide, the mapper defines over fifty strongly typed AST nodes. It systematically traverses the tree—from CompilationUnit down through classes, methods, parameter lists, and binary expression leaves.

Crucially, it maps these language-specific nodes into our universal SyntaxNode domain record while preserving the exact SourceSpan—the start and end line and column numbers. This coordinate metadata is the bridge that allows a user to click any node in the AST viewer and have the editor jump directly to that exact line in the code.

I’ll now hand over to Member 3 to explain how we handle multi-language ASTs, universal rules, and orchestration."

---

### Member 3: Universal Rules & Orchestration (Slides 9 – 12)

#### Slide 9 — Multi-Language AST Structural Contrast
"Thank you, Member 2. Moving from Java to Python and C++ reveals three completely different structural philosophies:

- Java enforces a strict nominal hierarchy: Packages contain Imports, which contain Classes, which contain Methods.
- Python permits loose script-level statements outside of any class, wrapped in indentation-based SuiteNode containers.
- C++ must interweave namespaces, preprocessor directives, and complex template declarations side-by-side.

Despite these fundamental differences, our universal SyntaxNode record—defined simply by a label, node type, source span, and child list—normalizes all three. This allows our UI tree viewer to render Java classes, Python scripts, or C++ templates using a single, unified visual component."

#### Slide 10 — Phase 3: Universal Rule Checking Suite
"While parsers detect grammar violations, Writely runs an additional suite of six modular Syntax Checkers that operate universally:

1. DelimiterMatchingChecker: Stack-based validation ensuring pairs of parentheses, brackets, and braces match.
2. StringAndCharacterLiteralChecker: Flags unterminated quotes and invalid escape sequences.
3. StatementTerminatorAndBlockChecker: Detects missing semicolons in Java and C++, and validates indentation blocks in Python.
4. OperatorSyntaxChecker: Catches adjacent illegal operators, like 'a ++* b'.
5. ControlStructureHeaderChecker: Verifies loop and conditional headers.
6. IdentifierSyntaxChecker: Enforces identifier rules and naming conventions.

Why separate these from parsing? Because universal rules provide richer, human-friendly repair suggestions, and adapters can dynamically enable or disable them based on their declared capabilities."

#### Slide 11 — Phase 4: Analysis Orchestrator Lifecycle
"Now let’s look at how the AnalysisOrchestrator ties this entire pipeline together. As shown in the lifecycle flow:

1. The workspace controller passes the SourcePayload to the orchestrator.
2. The registry resolves the required adapter for the target language.
3. The adapter tokenizes and parses the payload inside safe try-catch boundaries.
4. The orchestrator executes all capability-approved syntax checkers against the tokens and AST.
5. It merges all findings, applies our deduplication policy, and compiles the final AnalysisResult.

Step 4 introduces a critical problem: what happens when both the parser and a universal checker detect the same error?"

#### Slide 12 — Phase 4: ADR 0015 Canonical Supersession
"This brings us to one of our most important architectural decisions: ADR 0015 Canonical Supersession.

If a developer forgets a semicolon in Java, the internal Java parser flags it as a missing token, while our universal StatementTerminatorChecker also flags it. Showing both creates confusing duplicate error cards in the UI.

Under ADR 0015:
- When an internal parser diagnostic and a canonical rule diagnostic share the same CheckCategory and line/column coordinates, the canonical diagnostic supersedes the internal one.
- The generic parser error is suppressed, while the canonical diagnostic—which contains a standardized error code and an actionable suggested fix—is presented to the user.

This guarantees clean, deduplicated error reports every time.

Now, Member 4 will walk us through the reactive UI implementation and our key architectural tradeoffs."

---

### Member 4: Reactive UI & Design Tradeoffs (Slides 13 – 15)

#### Slide 13 — Phase 5: GUI Decoupling & Reactive Rendering
"Thank you, Member 3. A powerful analysis engine is useless if it freezes the user interface whenever the user types.

To guarantee a smooth 60 FPS typing experience, Writely strictly enforces UI thread hygiene:
- All heavy operations—lexing, parsing, and rule checking—run off the main thread inside a background JavaFX Task<AnalysisResult> managed by an executor service.
- When the task completes, results are dispatched safely back to the JavaFX Application Thread via Platform.runLater().

This atomically refreshes the token table, updates the recursive AST tree view, and repopulates the diagnostic panel without introducing any typing latency in the editor."

#### Slide 14 — Phase 5: Diagnostic Cards & Caret Navigation
"In the UI, diagnostics aren't just plain text—they are interactive cards.

Each card displays a clear visual hierarchy: severity badges, error codes, category tags, descriptive messages, and actionable fixes.

Even more importantly, we built a bidirectional coordinate mapping seam. Diagnostic findings store human-readable 1-based line and column coordinates, whereas the code editor operates on raw 0-based character indices. When a user clicks a diagnostic card, locationToOffset() converts those coordinates into an exact caret offset, automatically jumping to and highlighting the offending code span in the editor."

#### Slide 15 — Key Design Tradeoffs & Architectural Summary
"To conclude our walkthrough, let’s review the key architectural tradeoffs that shaped Writely:

1. In-Memory Pipeline: We rejected disk caching and serialization in favor of pure in-memory execution, maximizing throughput and simplicity.
2. Lightweight SyntaxNode: We avoided heavy third-party compiler frameworks like ANTLR or Eclipse AST, eliminating runtime bloat and keeping our domain model completely independent.
3. Fault Containment: Pipeline stages catch errors locally and emit diagnostics rather than throwing fatal exceptions that crash the app.
4. Two-Tier Diagnostics: Through ADR 0015, canonical rule checks cleanly supersede raw parser errors.
5. Declarative Capabilities: Language adapters declare what rules they support, eliminating spaghetti if/else logic in the core orchestrator.

In summary, Writely demonstrates that building a multi-language syntax analyzer doesn't require complex monolithic code. With clean architectural seams, we achieved high testability, frictionless language extensibility, and rock-solid UI stability.

Thank you for your time. Our team is now happy to open the floor for any questions!"
