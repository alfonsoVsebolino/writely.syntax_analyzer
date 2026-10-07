package com.writely.syntax_analyzer.domain;

/**
 * Standard categories of syntactical checks performed by the analyzer.
 */
public enum CheckCategory {
    DELIMITER_MATCH("Delimiter Match"),
    LITERAL_SYNTAX("Literal Syntax"),
    STATEMENT_TERMINATOR("Statement Terminator"),
    OPERATOR_SYNTAX("Operator Syntax"),
    CONTROL_HEADER("Control Header"),
    IDENTIFIER_NAMING("Identifier Naming");

    private final String displayName;

    CheckCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
