package com.writely.syntax_analyzer.app.guide;

import java.util.List;

/**
 * Canonical content provider for the first-run writely Onboarding Guide.
 */
public final class OnboardingGuideContent {

    public static final String TITLE_WHAT_WRITELY_IS = "What writely Is and Is Not";
    public static final String TITLE_INPUT_MODES = "Input Modes";
    public static final String TITLE_CHECK_CATEGORIES = "Check Categories";
    public static final String TITLE_READING_REPORT = "Reading the Analysis Report";
    public static final String TITLE_INGESTION_LIMITS = "Ingestion Limits and Supported Files";

    public static final List<String> SECTION_TITLES = List.of(
            TITLE_WHAT_WRITELY_IS,
            TITLE_INPUT_MODES,
            TITLE_CHECK_CATEGORIES,
            TITLE_READING_REPORT,
            TITLE_INGESTION_LIMITS
    );

    private static final GuideDocument DOCUMENT = buildDocument();

    private OnboardingGuideContent() {
    }

    public static GuideDocument getDocument() {
        return DOCUMENT;
    }

    private static GuideDocument buildDocument() {
        GuideSection section1 = new GuideSection(
                TITLE_WHAT_WRITELY_IS,
                new ParagraphBlock(
                        "writely performs static syntax analysis only. It parses and validates source code " +
                        "against formal grammatical rules, but never executes, compiles, or links analyzed code."
                ),
                new ParagraphBlock(
                        "An analysis status of PASSED means the source is syntactically valid according to the target " +
                        "language grammar. It does not indicate that the code will run correctly, terminate, or be free " +
                        "from semantic or logic bugs."
                ),
                new ParagraphBlock(
                        "Understanding the syntax versus semantic boundary is critical. For example:"
                ),
                new SnippetBlock("HelloWorld(print)"),
                new ParagraphBlock(
                        "This statement passes syntax analysis as a call expression with identifier argument. " +
                        "However, it fails at runtime with undefined name errors if HelloWorld or print is not defined. " +
                        "Semantic analysis and runtime execution are outside writely's static syntax scope."
                )
        );

        GuideSection section2 = new GuideSection(
                TITLE_INPUT_MODES,
                new ParagraphBlock(
                        "writely provides three distinct input modes for analyzing source code:"
                ),
                new BulletListBlock(
                        "Single Line: A single command or statement.",
                        "Code Snippet: An arbitrary multi-line block of source code.",
                        "File Upload: A program source file selected from the local file system."
                )
        );

        GuideSection section3 = new GuideSection(
                TITLE_CHECK_CATEGORIES,
                new ParagraphBlock(
                        "Static checks are grouped into six core categories, evaluated with language-specific rules:"
                ),
                new BulletListBlock(
                        "DELIMITER_MATCH: Balancing of (), [], {}.",
                        "LITERAL_SYNTAX: Unterminated or malformed string and character literals.",
                        "STATEMENT_TERMINATOR: Missing semicolons (;) in Java/C++, or indentation/colon (:) in Python.",
                        "OPERATOR_SYNTAX: Consecutive illegal operators, missing operands, invalid unary/binary usage.",
                        "CONTROL_HEADER: Structural validation of if, for, while, switch headers.",
                        "IDENTIFIER_NAMING: Illegal variable/function names or character violations."
                ),
                new ParagraphBlock(
                        "Language-specificity caveat: rules reflect language differences. Semicolons are required in Java/C++, " +
                        "while Python uses colons (:) and block indentation."
                )
        );

        GuideSection section4 = new GuideSection(
                TITLE_READING_REPORT,
                new ParagraphBlock(
                        "After running an analysis, the workspace displays comprehensive feedback across several report components:"
                ),
                new BulletListBlock(
                        "Status Pill: High-level status pill (PASSED/FAILED) reflecting overall syntax validity.",
                        "Metric Insights: Metric insights summarizing line count, token totals, and category breakdown.",
                        "Diagnostics Rows: Diagnostics rows detailing severity, category, location, and message.",
                        "Line Jump: Line jump navigation to quickly jump to the flagged source line in the editor."
                )
        );

        GuideSection section5 = new GuideSection(
                TITLE_INGESTION_LIMITS,
                new ParagraphBlock(
                        "To ensure fast and reliable operation, source ingestion adheres to defined boundaries:"
                ),
                new BulletListBlock(
                        "Supported Files: Python, Java, and C++ source files (.py, .java, .cpp, .hpp, .cc, .cxx, .h).",
                        "Size Limit: Files up to a 15 MB cap are supported for ingestion.",
                        "Binary Rejection: Binary files are rejected during ingestion to protect analyzer integrity."
                )
        );

        return new GuideDocument(section1, section2, section3, section4, section5);
    }
}
