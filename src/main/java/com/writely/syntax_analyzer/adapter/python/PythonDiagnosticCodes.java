package com.writely.syntax_analyzer.adapter.python;

/**
 * Standard diagnostic error codes emitted by the Python parser adapter.
 */
public final class PythonDiagnosticCodes {

    public static final String ERR_MISSING_COLON = "ERR_MISSING_COLON";
    public static final String ERR_MALFORMED_HEADER = "ERR_MALFORMED_HEADER";
    public static final String ERR_EXPECTED_INDENTED_BLOCK = "ERR_EXPECTED_INDENTED_BLOCK";
    public static final String ERR_UNEXPECTED_INDENT = "ERR_UNEXPECTED_INDENT";
    public static final String ERR_INVALID_SYNTAX = "ERR_INVALID_SYNTAX";
    public static final String ERR_UNCLOSED_DELIMITER = "ERR_UNCLOSED_DELIMITER";

    private PythonDiagnosticCodes() {
    }
}
