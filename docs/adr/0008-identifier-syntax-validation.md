# 8. Identifier Syntax Validation Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

Static syntactical analysis requires language-aware validation of identifiers and keyword usage across Java, Python, and C++. Errors in identifier syntax—such as identifiers erroneously beginning with digits, the presence of illegal characters within identifier lexemes, and the misplacement of reserved keywords into declaration or identifier positions—must be detected before or during AST construction.

Different programming languages enforce distinct lexical rules for identifiers and declaration positions:
- **Digits in Identifiers**: Across Java, Python, and C++, identifiers may contain digits but cannot start with a digit. At the lexical level, a numeric literal that immediately abuts an identifier or keyword token without whitespace or separator (e.g., `123var`, `0abc`, `1_foo`) constitutes a malformed identifier starting with a digit.
- **Illegal Characters**:
  - In Java, dollar signs (`$`) and underscores (`_`) are valid identifier characters (`Character.isJavaIdentifierStart` and `Character.isJavaIdentifierPart`), while unknown characters (such as `@`, `#`, `~`) abutting identifiers are illegal.
  - In Python and C++, the dollar sign (`$`) is illegal in standard identifiers. Additionally, unknown/malformed tokens directly abutting identifiers (e.g., `@var` in C++, `var#name` in Java/C++) must be flagged.
- **Keywords as Identifiers**:
  - In Java and C++, keywords cannot be used as identifier names in declaration contexts, such as following primitive/type keywords (`int class = 1;`, `void return()`), modifiers when not followed by another type keyword, or declaration keywords (`class int {`, `struct return {}`). In C++, valid multi-token type combinations (e.g., `long long`, `unsigned int`) and scoped enumerations (`enum class`, `enum struct`) must be permitted.
  - In Python, reserved keywords cannot immediately follow `def`, `class`, or `as` (e.g., `def for():`, `class while:`, `import math as if`).

## Decisions

1. **Syntax Checker Implementation**:
   Implement `IdentifierSyntaxChecker` implementing `SyntaxChecker` under `com.writely.syntax_analyzer.core.check`, operating under `CheckCategory.IDENTIFIER_NAMING`.

2. **Standard Diagnostic Codes**:
   - `ERR_IDENTIFIER_STARTS_WITH_DIGIT`: Flagged when a `LITERAL_NUMBER` token directly abuts an `IDENTIFIER` or `KEYWORD` token without intervening whitespace or separator (i.e. `prev.span().endLocation().charOffset() == next.span().startLocation().charOffset()`). Location points to `prev.startLocation()`, with suggested fix "Prefix identifier with a letter or underscore, or separate number from identifier".
   - `ERR_ILLEGAL_IDENTIFIER_CHAR`: Flagged for language-specific invalid characters in identifiers:
     - In Python and C++: Flag identifiers containing `$` or adjacent `UNKNOWN` tokens abutting identifiers.
     - In Java: Allow `$` and `_` as legal identifier characters; flag any adjacent `UNKNOWN` tokens directly abutting identifiers.
   - `ERR_KEYWORD_AS_IDENTIFIER`: Reserved keywords appearing in unambiguous declaration / identifier positions:
     - In Java/C++: Immediately following type keywords/primitives (`int`, `boolean`, `char`, `float`, `double`, `long`, `short`, `byte`, `void`, `auto`, etc.) or modifiers when not followed by another valid type keyword or modifier, and immediately following declaration keywords (`class`, `interface`, `enum`, `record`, `struct`, `namespace`).
     - In Python: Immediately following `def`, `class`, or `as`.

3. **Digit Abutting Detection**:
   Inspect consecutive tokens in the token stream. If a `LITERAL_NUMBER` token directly abuts an `IDENTIFIER` or `KEYWORD` (`prev.endOffset() == next.startOffset()`), emit `ERR_IDENTIFIER_STARTS_WITH_DIGIT` at the number's start location. This cleanly distinguishes valid numeric expressions separated by spaces or operators (e.g., `123 var`, `123 + var`) from malformed identifiers (`123var`, `0abc`, `1_foo`).

4. **Language-Specific Illegal Character Validation**:
   - Check whether `IDENTIFIER` tokens contain `$` in Python or C++ environments, pinpointing `token.startLocation()`.
   - Inspect `UNKNOWN` tokens. If an `UNKNOWN` token directly or contiguously abuts an adjacent identifier or keyword token without whitespace, flag the offending unknown character token with `ERR_ILLEGAL_IDENTIFIER_CHAR`.
   - In Java, identifiers containing `$` are considered valid and produce no diagnostic.

5. **Keyword as Identifier Detection Heuristics**:
   - Filter trivia tokens to analyze non-trivia adjacent pairs `(current, next)`.
   - In Python, if `current` is `def`, `class`, or `as` and `next` is a `KEYWORD`, emit `ERR_KEYWORD_AS_IDENTIFIER`.
   - In Java and C++, if `current` is a declaration keyword and `next` is a `KEYWORD` (excluding C++ scoped enumeration specifiers `enum class` and `enum struct`), emit `ERR_KEYWORD_AS_IDENTIFIER`.
   - If `current` is a type keyword and `next` is a `KEYWORD` (excluding legal C++ compound type combinations such as `long long`, `unsigned int`), emit `ERR_KEYWORD_AS_IDENTIFIER`.
   - If `current` is a modifier and `next` is a `KEYWORD` that is not a modifier, declaration keyword, or type keyword, emit `ERR_KEYWORD_AS_IDENTIFIER`.

6. **Safety, Null Validation, and Clean Non-Cascading Recovery**:
   - Enforce non-null `payload` and `tokens` preconditions, returning empty unmodifiable lists on empty token inputs.
   - Single-pass non-cascading recovery ensures that an identifier error does not propagate false positive errors downstream in the source file.
   - Diagnostics are sorted by `SourceLocation` to maintain monotonic source order.

## Consequences

- Consistent, language-aware identifier syntax validation across Java, Python, and C++.
- Accurate detection of digit-prefixed identifiers, illegal characters, and misplaced keywords.
- Zero false positives on valid language constructs (e.g., Java `$`, C++ `enum class`, C++ `long long`, Python decorators).
- Provides actionable error codes and suggested fixes for IDE feedback and downstream diagnostics.
