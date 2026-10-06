# Project Specification & System Requirements: Multi-Input Syntactical Analyzer

## 1. Project Metadata & Overview

- **Course / Subject:** Computer Programming / Software Development
- **Project Type:** Individual or Group (Max 3 Members)
- **Programming Language:** Language Agnostic (Student Choice: Python, Java, C#, C++, JavaScript/TypeScript, Kotlin, Go, etc.)
- **Interface Type:** Flexible (CLI, GUI, or Web Application)
- **Total Weight:** 100 Points (Includes 25% Oral Defense)

### Project Overview
The Syntactical Analyzer Application is a static syntax analysis tool designed to parse source code inputs, tokenize the contents, evaluate syntax against predefined syntactical rules, and produce structured analysis reports highlighting successful validations or pinpointing syntax errors with exact line numbers and explanations.

### Key Learning Outcomes
- **Multi-Modal Input Processing:** Handle single statements, code snippets, and uploaded files seamlessly.
- **Lexical & Syntactical Analysis:** Parse programming constructs and validate structural syntax rules.
- **Error Detection & Reporting:** Detect and report specific syntax violations in a clean, human-readable format.
- **Technical Defense:** Explain parsing algorithms, tokenization logic, and edge case handling during oral defense.

---

## 2. System Requirements & Functional Scope

### 2.1 Input Capabilities (Mandatory)
The application **MUST** support all three (3) of the following input modes:

- [ ] **Input Mode 1 — Single Programming Command / Line:**
  - Allows entry of a single statement/command via an input field or CLI prompt (e.g., `x = 10` or `System.out.println("Hello");`).
- [ ] **Input Mode 2 — Block of Commands / Code Snippet:**
  - Allows entry of multi-line source code blocks directly via a text area, prompt, or multi-line buffer.
- [ ] **Input Mode 3 — Program File Upload:**
  - Allows uploading or selecting a source file from the local filesystem (`.py`, `.java`, `.cpp`, `.cs`, `.js`, `.txt`, etc.).
  - The application reads and analyzes the entire file content.

---

### 2.2 Syntax Error Checking Scope
The analyzer must implement validation for **at least four (4)** of the following syntax checks:

- [ ] **1. Delimiter & Bracket Matching:**
  - Verify balanced parentheses `()`, square brackets `[]`, and curly braces `{}`.
- [ ] **2. String & Character Literals:**
  - Detect unclosed single/double quotes in string declarations.
- [ ] **3. Statement Terminators:**
  - Check for missing semicolons `;` (for Java/C/C#/C++) or improper indentation/colon usage `:` (for Python).
- [ ] **4. Operator Syntax:**
  - Detect invalid consecutive operators (e.g., `x = 5 + * 2`) or missing operands.
- [ ] **5. Control Structure Headers:**
  - Validate syntax structure for control statements (`if`, `for`, `while`, `switch`).
- [ ] **6. Identifier Naming Rules:**
  - Flag illegal variable/function names (e.g., starting with numbers or containing illegal characters).

> **Scope & Language Flexibility Note:**
> The analyzer may focus on a specific target language family (e.g., Java/C-style syntax, Python syntax, JavaScript) or enforce universal syntax rules. The output must provide both detailed line-by-line findings and summary totals for each category.

---

## 3. Input & Output Reference Specifications

### Example 1: Valid Code Input (No Errors)

#### Sample Input (Input Mode 2 — Multi-Line Block)
```python
x = 10
if (x > 5):
    print("Value is valid")
```

#### Expected Output Report
```text
SYNTACTICAL ANALYSIS REPORT
Status: PASSED (0 Syntax Errors Found)
Total Lines Analyzed: 3
Target Syntax Rule: Python / C-Style Hybrid

LINE BREAKDOWN & SYNTAX CHECK:
Line 1: [x = 10]
- Syntax Check: Variable Assignment -> OK
- Delimiters: Balanced
Line 2: [if (x > 5):]
- Syntax Check: Conditional Statement Header -> OK
- Delimiters: Parentheses () Balanced | Colon ':' Present
Line 3: [print("Value is valid")]
- Syntax Check: Function Call / Output Statement -> OK
- Delimiters: Parentheses () Balanced | String Quotes "" Balanced

SUMMARY:
- Total Tokens Parsed: 14
- Delimiter Balance: OK
- Syntax Validation: SUCCESSFUL
```

---

### Example 2: Code Input With Errors Detected

#### Sample Input (Input Mode 3 — File Upload)
```python
x = 10
if (x > 5
    print("Value is valid)
y = 20 + * 5
```

#### Expected Output Report
```text
SYNTACTICAL ANALYSIS REPORT
Status: FAILED (3 Syntax Errors Detected)
Total Lines Analyzed: 4

SYNTAX ERROR DETAILS:
[ERROR 1] Line 2: if (x > 5
- Category: Delimiter / Bracket Mismatch
- Details: Unclosed parenthesis '('. Expected ')' before line end.

[ERROR 2] Line 3:     print("Value is valid)
- Category: String Literal Error
- Details: Unclosed string literal. Missing matching double quote '"'.

[ERROR 3] Line 4: y = 20 + * 5
- Category: Invalid Operator Sequence
- Details: Consecutive binary operators '+ *' without an operand in between.

SUMMARY:
- Total Lines Checked: 4
- Valid Lines: 1
- Flagged Lines: 3 (Lines 2, 3, 4)
- Action Required: Correct highlighted syntax errors above.
```

---

## 4. Project Deliverables & Submission Checklist

- [ ] **Executable / Source Code Repository:**
  - Complete, well-commented source code uploaded to GitHub/GitLab or submitted as a zip archive with build/run instructions.
- [ ] **User Manual & System Documentation (3–5 pages, PDF/Word):**
  - Architecture breakdown, parsing logic, regular expressions/AST libraries used, and operational instructions for all 3 input modes.
- [ ] **Application Demonstration Video (3–5 minutes):**
  - Recorded demonstration showing live analysis across all 3 input modes with results clearly visible.
- [ ] **Oral Defense & Q&A Session (10–15 minutes):**
  - Live technical defense during presentation week; individual technical questioning for all group members.

---

## 5. Grading Rubric (100% Total)

### Core Assessment Summary
| Assessment Criteria | Description / Key Aspects | Weight |
| :--- | :--- | :---: |
| **1. Functional Correctness & Feature Completeness** | Accurate detection across all required categories and across all 3 input modes (single line, snippet, file upload). | 30% |
| **2. Parsing Architecture & Error Detection** | Clean code structure, modular design, effective use of regex/AST/parser, robust error handling, and parsing logic. | 20% |
| **3. User Interface & User Experience** | Intuitive input handling, clear display of analysis results, formatting of summaries, and error reporting. | 15% |
| **4. System Documentation & Deliverables** | Completeness of user manual, repository organization, clear execution guide, and demo video quality. | 10% |
| **5. Oral Defense & Q&A (Individual)** | Demonstrated mastery of codebase, explanation of parsing logic, and handling of technical edge cases. | 25% |

---

### Detailed Performance Matrix

| Criteria | Exemplary (90–100%) | Proficient (75–89%) | Developing (50–74%) | Unsatisfactory (<50%) |
| :--- | :--- | :--- | :--- | :--- |
| **Functional Completeness (30%)** | All 3 input modes work flawlessly. Accurately extracts categories with 0–5% error rate. | All 3 modes work. Extracts categories accurately with minor parsing errors (5–15%). | 2 input modes work. Missing categories or noticeable parsing bugs (15–30%). | Major functional failure. Supports only 1 input mode or fails to analyze core constructs. |
| **Code Architecture & Parsing (20%)** | Clean, modular, well-commented code. Robust regex/AST logic with complete error handling. | Modular code structure. Minor redundant logic or minor gaps in unhandled syntax. | Monolithic or messy code. Minimal error handling; breaks on unusual code syntax. | Poor code structure, unreadable code, frequent runtime crashes. |
| **UI / UX (15%)** | Sleek, user-friendly CLI/GUI. Clear tabbed/sectioned output report with visual counts. | Clean, functional UI/CLI. Output is organized and easy to interpret. | Cluttered UI/CLI output. Lack of formatting makes results hard to read. | Confusing or non-functional interface. Missing output formatting. |
| **Documentation & Video (10%)** | Comprehensive, professional manual, clean repository setup, flawless demo video. | Complete manual and repository. Demo video covers main features clearly. | Incomplete documentation or repository missing setup steps. | Missing user manual, repository unavailable, or demo video omitted. |
| **Oral Defense & Q&A (25%)** | Flawless technical mastery. Thoroughly explains parsing algorithms, regex, and handles edge cases with ease. | Solid understanding of codebase and architecture. Answers core questions accurately with minor hesitation. | Basic understanding. Struggles to explain specific logic or regex details written by team. | Unable to explain project code, algorithms, or contribution. Potential academic dishonesty. |

---

## 6. Oral Defense Preparation & Question Bank

### Category A: Parsing & Tokenization Logic
- *"How does your application tokenize input text before checking for syntax errors?"*
- *"What data structure did you use to verify balanced delimiters (parentheses/braces), and how does it handle nested brackets?"*
- *"How does your algorithm distinguish between an operator used correctly (e.g., `+5`) and an operator error (e.g., `+*`)?"*

### Category B: Input & File Processing
- *"How does your program handle line breaks, tabs, and comments when analyzing uploaded files?"*
- *"What error handling is in place if a user uploads a blank file or a non-text file?"*

### Category C: Edge Case Handling
- *"How do you prevent string literal contents (e.g., `\"if (x > 0)\"`) from being parsed as actual code syntax?"*
- *"How does your parser recover after finding an error on line 2 so it can continue checking line 3?"*

### Category D: Individual Contribution
- *"Which specific module or syntax check did you personally implement? Walk us through your source code."*