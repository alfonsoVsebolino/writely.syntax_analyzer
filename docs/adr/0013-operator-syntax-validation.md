# 13. Operator Syntax Validation Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

Operators in programming languages exhibit polymorphic syntax roles depending on language grammar and expression context:
- **Prefix Unary Operators**: Applied preceding an operand (e.g., `+x`, `-x`, `!flag`, `~mask`, `++i`, `--i`). In C++, `*ptr` (pointer dereference / type) and `&ref` (address-of / reference) act as valid unary prefix operators, alongside `::` (global namespace resolution). In Python, `*args` and `**kwargs` act as unary unpack operators in parameters, argument lists, and container displays, alongside keyword `not`.
- **Postfix Unary Operators**: Applied following an operand (e.g., `i++`, `i--` in Java and C++). Python does not support postfix increment or decrement.
- **Binary & Assignment Operators**: Require both left and right operands (e.g., `+`, `-`, `*`, `/`, `%`, `==`, `!=`, `<`, `>`, `<=`, `>=`, `&&`, `||`, `&`, `|`, `^`, `<<`, `>>`, `>>>`, `=`, `+=`, `-=`, etc.).
- **Special Constructs & Delimiters**: Java diamond operators (`new ArrayList<>()`), C++ template argument lists (`vector<int>`, `vector<int*>`), C++ pointer-to-member operators (`.*`, `->*`), Python multi-token comparison keywords (`is not`, `not in`), integer division (`//`), and exponentiation (`**`).

Without dedicated language-aware operator validation, invalid sequences such as `+ *`, `/ *`, `== =`, `&& ||`, `% /`, or missing operands like `a + ;` and `* b` (in Java) could corrupt downstream parsing or yield misleading error cascades. Conversely, naive consecutive-token matching triggers false positives on valid constructs like `x + +y`, `x - -y`, `x = -1`, `!flag`, `~mask`, `++i`, `i++`, `*ptr`, `&ref`, `*args`, and `is not`.

A dedicated syntax checker under `com.writely.syntax_analyzer.core.check` is required to enforce operator rules across Java, Python, and C++ with exact 1-based source location coordinates and clean, non-cascading recovery.

## Decisions

1. **Syntax Checker Implementation**:
   Implement `OperatorSyntaxChecker` implementing `SyntaxChecker` under `com.writely.syntax_analyzer.core.check`, operating under `CheckCategory.OPERATOR_SYNTAX`.

2. **Standard Diagnostic Codes**:
   - `ERR_INVALID_OPERATOR_SEQUENCE`: Emitted when two consecutive operator tokens form an invalid sequence where the second operator cannot legally act as a prefix unary operator following the first (e.g., `+ *`, `/ *`, `* /`, `== =`, `&& ||`, `% /`). The offending second token is reported at its 1-based start location.
   - `ERR_MISSING_OPERAND`: Emitted when a binary operator is missing its left operand (e.g., `* b` in Java or at start of expression) or its right operand (e.g., `a + ;`, `5 *` at EOF, or preceding closing delimiters `)`, `]`, `}`, `,` or Python unbracketed newline). The operator token itself is reported at its 1-based start location.
   - `ERR_INVALID_UNARY_OPERATOR`: Emitted when a unary operator is applied invalidly:
     - Missing operand for pure unary operator (e.g., `! ;`, `~ ;`, `++ ;`, `not ;` in Python).
     - Prefix or postfix increment/decrement applied to literals (e.g., `5++`, `++5`, `"str"++`).
     - Chained increment/decrement (e.g., `i++ ++`, `++ ++ i`).

3. **Language-Specific Operator Tables & Invariants**:
   - **Java**:
     - Prefix unary: `+`, `-`, `!`, `~`, `++`, `--`.
     - Pure binary: `*`, `/`, `%`, `==`, `!=`, `<=`, `>=`, `&&`, `||`, `&`, `|`, `^`, `<<`, `>>`, `>>>`, `=`, `+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, `>>=`, `>>>=`, `->`, `::`, `?`.
     - Diamond operator `<>` recognized as valid generic syntax.
     - `*` is strictly binary in Java; `* b` at expression start is flagged as `ERR_MISSING_OPERAND`.
   - **C++**:
     - Prefix unary: `+`, `-`, `!`, `~`, `++`, `--`, `*` (dereference / pointer), `&` (address-of / reference), `::` (global namespace).
     - Valid pointer combinations: `**`, `*&`, `&*` allowed for double pointers and dereferenced references.
     - Template closing with pointer/reference (`<Type*>`, `<Type&>`) guarded against consecutive operator errors.
     - Binary operators: `.*`, `->*`, `<=>`, `<<`, `>>`, `&&`, `||`, etc.
   - **Python**:
     - Prefix unary: `+`, `-`, `~`, `not`, `*` (unpack), `**` (kwargs unpack).
     - Pure binary operators and keywords: `/`, `//`, `%`, `==`, `!=`, `<`, `>`, `<=`, `>=`, `&`, `|`, `^`, `<<`, `>>`, `=`, `:=`, `+=`, `**=`, `and`, `or`, `is`, `in`.
     - Valid multi-token keyword pairs: `is not`, `not in`, `and not`, `or not`, `not not`.
     - Single-token operators: `//` (integer division) and `**` (exponentiation).
     - Newline sensitivity: Track bracket depth (`( )`, `[ ]`, `{ }`) so that newlines inside brackets are treated as trivia, while unbracketed newlines correctly delimit statement boundaries for missing right operand detection.

4. **False Positive Prevention & Coordinate Precision**:
   - Differentiate postfix `++` / `--` (preceded by identifier or `)`, `]`) from prefix `++` / `--` (preceded by expression boundary, operator, or delimiter). Postfix operators preceding `;`, `)`, `]`, `,` or binary operators (`i++ + j`) do not expect a right operand.
   - Distinguish generic/template closing `>` from binary comparison `>`: when `>` follows a type or pointer within an enclosing type header, it is recognized as closing delimiter rather than an operator.
   - Report exact 1-based `SourceLocation` corresponding to the offending token in the token stream.
   - Enforce non-null assertions on `payload` and `tokens`, returning empty unmodifiable lists on empty token streams, and sort diagnostics monotonically by source location.

## Consequences

- Full syntactical operator coverage for Java, Python, and C++ conforming to Issue #22 specifications.
- Clean distinction between invalid operator sequences, missing binary operands, and malformed unary operators.
- Zero false positives on standard language constructs including unary chains, C++ pointers/references, Python unpacking/keywords, and Java diamond/lambda syntax.
- Non-cascading single-pass error recovery reporting exact line and column coordinates for multiple defects in a single compilation unit.
