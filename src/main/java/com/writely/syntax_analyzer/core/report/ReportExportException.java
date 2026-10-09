package com.writely.syntax_analyzer.core.report;

/**
 * Unchecked exception thrown when analysis report formatting or file export fails.
 */
public class ReportExportException extends RuntimeException {

    public ReportExportException(String message) {
        super(message);
    }

    public ReportExportException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReportExportException(Throwable cause) {
        super(cause);
    }
}
