# Member A Presentation Guide: Foundation, Ingestion & Lexical Tokenization

## 1. Role & Presentation Metadata
* **Assigned Stage**: Pipeline Phase 0 & Phase 1 (Architecture Seams, Domain Models, Ingestion & Lexical Tokenization).
* **Covered Slides**: Slide 1 to Slide 6 in [`writely-architecture-presentation.md`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/docs/lecture/writely-architecture-presentation.md).
* **Target Timing**: ~4 to 5 minutes.
* **Core Mission**: Explain how raw code enters Writely, gets normalized across operating systems, and transforms into a stream of coordinate-aware tokens while preserving trivia.

---

## 2. Intuitive Mental Model
> **The Airport Security & Luggage Scanner Analogy:**
> Raw source code arriving from user keystrokes or files is like passengers arriving at an airport terminal. Before passengers can board an aircraft (the AST parser), they must go through luggage screening.
> * **Ingestion (`SourceIngestionService`)** is the terminal entrance: it checks tickets (UTF-8 encoding), removes contraband (inconsistent Windows `\r\n` carriage returns), and stamps a clean boarding pass (`SourcePayload`).
> * **Tokenization (`Tokenizer`)** is the security checkpoint: it breaks passenger groups into distinct individuals (`Token`), identifying their role (passengers = keywords/identifiers; personal luggage = trivia comments/whitespace) and recording their exact terminal gate (`SourceSpan` line and column coordinates).

---

## 3. Relevant Codebase Files & Deep Overview

### 3.1. The Pure Domain Foundation (`domain/`)
* [`SourcePayload.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/domain/SourcePayload.java):
  * **Role**: Immutable record encapsulating the raw input before analysis.
  * **Fields**: `content` (normalized `String`), `language` (`Language` enum: JAVA, PYTHON, CPP), `inputMode` (`InputMode` enum: SINGLE_LINE, CODE_SNIPPET, FILE_UPLOAD), `origin` (optional filename or descriptor).
  * **Why It Matters**: Prevents primitive obsession (`String text`) and carries context across pipeline stages.
* [`Token.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/domain/Token.java) & [`TokenType.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/domain/TokenType.java):
  * **Role**: Atomic building block of syntax.
  * **Fields**: `type` (`TokenType`), `lexeme` (exact text slice), `span` (`SourceSpan`), `isTrivia` (`boolean`).
  * **Why It Matters**: Unlike standard compiler tokens that discard non-semantic elements, `Token` tracks whether it is trivia.
* [`SourceLocation.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/domain/SourceLocation.java) & [`SourceSpan.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/domain/SourceSpan.java):
  * **Role**: 1-based text coordinate geometry.
  * **Fields**: `line`, `column`, `offset` (0-based character index), `length`.
  * **Why It Matters**: UI editors (like JavaFX `TextArea`) require character offsets for caret positioning, while humans require 1-based line/column numbers. `SourceSpan` bridges both worlds.

### 3.2. Source Ingestion Layer (`core/ingestion/`)
* [`SourceIngestionService.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/ingestion/SourceIngestionService.java) & [`DefaultSourceIngestionService.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/ingestion/DefaultSourceIngestionService.java):
  * **Role**: Ingests, validates, and normalizes incoming code.
  * **Key Mechanics**:
    1. Null & empty checks throwing `IngestionException(IngestionErrorCode.EMPTY_INPUT)`.
    2. Newline sanitization: converts all `\r\n` (Windows) and `\r` (legacy Mac) to standard `\n` (Unix).
    3. UTF-8 validation: catches illegal byte sequences early.
  * **Why It Matters**: If Windows `\r\n` newlines aren't normalized, downstream character offset math slips by 1 byte per line, causing editor squiggly lines to highlight the wrong words!

### 3.3. Lexical Tokenization Engine (`core/tokenization/`)
* [`SourceCharStream.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/tokenization/SourceCharStream.java):
  * **Role**: Low-level cursor over `SourcePayload.content()`.
  * **Methods**: `peek()`, `peek(int k)`, `advance()`, `match(char expected)`, tracking current line, column, and offset.
* [`Tokenizer.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/tokenization/Tokenizer.java) & [`TokenizerFactory.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/tokenization/TokenizerFactory.java):
  * **Role**: Strategy contract for language lexers.
* [`JavaTokenizer.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/tokenization/JavaTokenizer.java):
  * Handles Java-specific lexemes: annotations (`@Override`), text blocks (`"""`), hex/binary numeric literals, standard curly braces.
* [`PythonTokenizer.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/tokenization/PythonTokenizer.java):
  * **Key Mechanics**: Maintains an integer indentation stack (`Deque<Integer> indentStack`).
  * Emits synthetic `INDENT` when spaces increase, synthetic `DEDENT` when indentation drops, and significant `NEWLINE` tokens.
* [`CppTokenizer.java`](file:///home/alfonsovsebolino/Documents/Programming_Languages/syntax_analyzer/src/main/java/com/writely/syntax_analyzer/core/tokenization/CppTokenizer.java):
  * Isolates preprocessor directives (`#include`, `#define`, `#pragma`) as dedicated tokens, preventing downstream checkers from mistaking `#` for illegal operator syntax.

---

## 4. Architectural Deep Dive: The Trivia Channel Architecture

Traditional batch compilers (e.g. `javac`, `gcc`) immediately discard comments and whitespace to save memory:
```
Raw: "int x = 10; // counter" ──> Compiler Lexer ──> [int] [x] [=] [10] [;] (comment destroyed)
```
In an interactive IDE and syntax analyzer like Writely, destroying comments breaks the user experience:
1. Syntax checkers need to verify whether comments are properly terminated (`/* ... */`).
2. The UI token table needs to display every token in the document.
3. Syntax highlighters need to colorize comments in green and whitespace accurately.

**Writely's Solution**:
Every `Token` contains a boolean flag `isTrivia()`.
* Semantic tokens (`int`, `x`, `=`, `10`, `;`): `isTrivia == false`.
* Trivia tokens (whitespace, comments): `isTrivia == true`.
Parsers filter `tokens.stream().filter(t -> !t.isTrivia())`, while checkers and UI tables receive the full unfiltered stream!

---

## 5. Spoken Presentation Script

### Slide 1: Title & Overview (0:00 – 0:45)
> *"Good morning everyone. Today our team is presenting the architecture of Writely, a multi-input static syntax analyzer built for Java, Python, and C++. I am Member A, and I will be covering our architectural foundation, our input ingestion pipeline, and our lexical tokenization system.*
>
> *Building a syntax analyzer that handles multiple languages without turning into spaghetti code requires clean architectural seams. Today, we'll demonstrate how Writely decouples every stage—from raw keystrokes to ASTs, universal rule checkers, and reactive GUI rendering."*

### Slide 2: Architectural Boundaries & Module Seams (0:45 – 1:30)
> *"Turning to Slide 2, Writely follows Clean Architecture principles. Take a look at the dependency graph.*
>
> *At the center is our `domain` package. It consists purely of immutable records—`Token`, `SyntaxNode`, `Diagnostic`, and `SourcePayload`. Notice the arrows: `core`, `adapter`, and `app` all point inward toward `domain`. `domain` has zero dependencies on any external framework or UI library.*
>
> *This means if we wanted to replace our JavaFX desktop interface with a command-line tool or a web service tomorrow, zero lines of code in `domain`, `core`, or `adapter` would change."*

### Slide 3: The Universal Seam (Quick Mention) (1:30 – 1:50)
> *"Slide 3 introduces our `ParserAdapter` interface. While Member B will dive deep into its parsing mechanics, notice that the adapter is responsible for both tokenization and AST parsing. This interface acts as the strict contract isolating language-specific grammars from our core engine."*

### Slide 4: Phase 1 — Ingestion & Source Normalization (1:50 – 2:40)
> *"Now let's examine Phase 1 on Slide 4. Every analysis request begins in `SourceIngestionService`.*
>
> *Writely accepts three input modes: single-line statements, multi-line unsaved scratchpad snippets, and uploaded files. But before any code reaches a tokenizer, it must be normalized.*
>
> *Why is ingestion normalization critical? Different operating systems use different line endings—Windows uses `\r\n`, whereas Linux uses `\n`. If a user uploads a Windows file and we calculate line and column coordinates on raw text, character offsets drift by one byte on every single line. When the user clicks an error in the UI, the editor would highlight the wrong words. `SourceIngestionService` sanitizes line endings and enforces UTF-8, producing an immutable `SourcePayload` with guaranteed mathematical consistency."*

### Slide 5 & 6: Lexical Analysis & Token Channels (2:40 – 4:00)
> *"On Slides 5 and 6, the clean `SourcePayload` is passed to our `Tokenizer` implementations via `SourceCharStream`.*
>
> *Writely uses a dual-channel token model. Standard batch compilers discard whitespace and comments immediately. But as an interactive tool, Writely cannot do that—we need to inspect unclosed block comments, highlight syntax, and display complete token lists. Our tokens carry an `isTrivia` flag: parsers see only semantic code, while checkers and UI components see the entire concrete syntax.*
>
> *Finally, Slide 6 contrasts how our tokenizers adapt to language differences:
> - Python is whitespace-sensitive, so `PythonTokenizer` maintains an internal indentation stack, generating synthetic `INDENT` and `DEDENT` tokens.
> - C++ isolates `#include` and `#define` preprocessor directives into distinct tokens so downstream checkers don't mistake `#` for an illegal operator.
> - Java handles annotations, character literals, and text blocks.*
>
> *Now, once we have this clean stream of tokens, how do we transform them into a hierarchical tree? I'll pass the floor to Member B to explain our Language Adapters and AST construction."*

---

## 6. Handoff Cue & Anticipated Q&A
* **Handoff Cue**: *"And with our tokens cleanly categorized and coordinates mapped, I will hand it over to Member B to explain our Parser Adapters and Abstract Syntax Tree generation."*
* **Likely Question**: *"Why did you write custom tokenizers instead of using regex or Lex/Flex?"*
  * **Answer**: *"Regex-based tokenizers struggle with nested constructs, multi-line comment states, and indentation stacks (like Python's `INDENT`/`DEDENT`). Writing custom tokenizers over `SourceCharStream` gave us zero-dependency performance, precise lookahead control, and exact coordinate tracking for `SourceSpan` without external runtime bloat."*
