package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.domain.CheckCategory;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Encapsulates language-specific syntactical capabilities and rules
 * exposed by a parser adapter to downstream analysis passes.
 */
public record RuleCapabilities(
    Set<CheckCategory> supportedCategories,
    boolean requiresStatementTerminators,
    boolean supportsIndentationBlocks,
    Set<String> keywords,
    Set<String> operators,
    Set<String> controlKeywords
) {
    public RuleCapabilities {
        supportedCategories = supportedCategories == null ? Set.of() : Set.copyOf(supportedCategories);
        keywords = keywords == null ? Set.of() : Set.copyOf(keywords);
        operators = operators == null ? Set.of() : Set.copyOf(operators);
        controlKeywords = controlKeywords == null ? Set.of() : Set.copyOf(controlKeywords);
    }

    public boolean supportsCategory(CheckCategory category) {
        return category != null && supportedCategories.contains(category);
    }

    public boolean isKeyword(String word) {
        return word != null && keywords.contains(word);
    }

    public boolean isOperator(String op) {
        return op != null && operators.contains(op);
    }

    public boolean isControlKeyword(String word) {
        return word != null && controlKeywords.contains(word);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RuleCapabilities forJava() {
        return builder()
            .supportedCategories(Set.of(CheckCategory.values()))
            .requiresStatementTerminators(true)
            .supportsIndentationBlocks(false)
            .keywords(JAVA_KEYWORDS)
            .operators(JAVA_OPERATORS)
            .controlKeywords(JAVA_CONTROL_KEYWORDS)
            .build();
    }

    public static RuleCapabilities forPython() {
        return builder()
            .supportedCategories(Set.of(CheckCategory.values()))
            .requiresStatementTerminators(false)
            .supportsIndentationBlocks(true)
            .keywords(PYTHON_KEYWORDS)
            .operators(PYTHON_OPERATORS)
            .controlKeywords(PYTHON_CONTROL_KEYWORDS)
            .build();
    }

    public static RuleCapabilities forCpp() {
        return builder()
            .supportedCategories(Set.of(CheckCategory.values()))
            .requiresStatementTerminators(true)
            .supportsIndentationBlocks(false)
            .keywords(CPP_KEYWORDS)
            .operators(CPP_OPERATORS)
            .controlKeywords(CPP_CONTROL_KEYWORDS)
            .build();
    }

    public static class Builder {
        private Set<CheckCategory> supportedCategories = Set.of(CheckCategory.values());
        private boolean requiresStatementTerminators = false;
        private boolean supportsIndentationBlocks = false;
        private Set<String> keywords = Set.of();
        private Set<String> operators = Set.of();
        private Set<String> controlKeywords = Set.of();

        public Builder supportedCategories(Collection<CheckCategory> categories) {
            this.supportedCategories = categories == null ? Set.of() : Set.copyOf(categories);
            return this;
        }

        public Builder requiresStatementTerminators(boolean requiresStatementTerminators) {
            this.requiresStatementTerminators = requiresStatementTerminators;
            return this;
        }

        public Builder supportsIndentationBlocks(boolean supportsIndentationBlocks) {
            this.supportsIndentationBlocks = supportsIndentationBlocks;
            return this;
        }

        public Builder keywords(Collection<String> keywords) {
            this.keywords = keywords == null ? Set.of() : Set.copyOf(keywords);
            return this;
        }

        public Builder operators(Collection<String> operators) {
            this.operators = operators == null ? Set.of() : Set.copyOf(operators);
            return this;
        }

        public Builder controlKeywords(Collection<String> controlKeywords) {
            this.controlKeywords = controlKeywords == null ? Set.of() : Set.copyOf(controlKeywords);
            return this;
        }

        public RuleCapabilities build() {
            return new RuleCapabilities(
                supportedCategories,
                requiresStatementTerminators,
                supportsIndentationBlocks,
                keywords,
                operators,
                controlKeywords
            );
        }
    }

    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "record", "sealed",
        "permits", "non-sealed", "var", "yield"
    );

    private static final Set<String> JAVA_OPERATORS = Set.of(
        ">>>=", ">>>", ">>=", "<<=",
        "++", "--", "==", "!=", "<=", ">=", "&&", "||",
        "<<", ">>", "+=", "-=", "*=", "/=", "%=", "&=",
        "|=", "^=", "->", "::",
        "=", "+", "-", "*", "/", "%", "<", ">", "!",
        "&", "|", "^", "~", "?"
    );

    private static final Set<String> JAVA_CONTROL_KEYWORDS = Set.of(
        "if", "else", "for", "while", "do", "switch", "case", "default",
        "try", "catch", "finally", "throw", "return", "break", "continue", "yield"
    );

    private static final Set<String> PYTHON_KEYWORDS = Set.of(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
        "while", "with", "yield", "match", "case"
    );

    private static final Set<String> PYTHON_OPERATORS = Set.of(
        "**=", "//=", "<<=", ">>=",
        "**", "//", ":=", "<<", ">>", "<=", ">=", "==", "!=",
        "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
        "+", "-", "*", "/", "%", "@", "&", "|", "^", "~",
        "<", ">", "=", "and", "or", "not", "is", "in"
    );

    private static final Set<String> PYTHON_CONTROL_KEYWORDS = Set.of(
        "if", "elif", "else", "for", "while", "try", "except", "finally",
        "with", "match", "case", "break", "continue", "return", "raise", "pass"
    );

    private static final Set<String> CPP_KEYWORDS = Set.of(
        "asm", "auto", "bool", "break", "case", "catch", "char", "class",
        "const", "constexpr", "continue", "default", "delete", "do", "double",
        "else", "enum", "explicit", "export", "extern", "false", "float",
        "for", "friend", "goto", "if", "inline", "int", "long", "mutable",
        "namespace", "new", "noexcept", "nullptr", "operator", "private",
        "protected", "public", "register", "return", "short", "signed",
        "sizeof", "static", "struct", "switch", "template", "this",
        "thread_local", "throw", "true", "try", "typedef", "typeid",
        "typename", "union", "unsigned", "using", "virtual", "void",
        "volatile", "wchar_t", "while"
    );

    private static final Set<String> CPP_OPERATORS = Set.of(
        "->*", "<=>", "<<=", ">>=",
        "::", "->", ".*", "++", "--", "==", "!=", "<=", ">=",
        "&&", "||", "<<", ">>", "+=", "-=", "*=", "/=", "%=",
        "&=", "|=", "^=",
        "=", "+", "-", "*", "/", "%", "<", ">", "!",
        "&", "|", "^", "~", "?"
    );

    private static final Set<String> CPP_CONTROL_KEYWORDS = Set.of(
        "if", "else", "for", "while", "do", "switch", "case", "default",
        "try", "catch", "throw", "return", "break", "continue", "goto"
    );
}
