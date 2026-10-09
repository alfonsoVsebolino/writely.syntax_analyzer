package com.writely.syntax_analyzer.core.report;

import com.writely.syntax_analyzer.domain.AnalysisResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Public facade service for formatting and exporting syntactical analysis reports.
 */
public class ReportExportService {

    private final TextReportFormatter textFormatter;
    private final JsonReportFormatter jsonFormatter;

    public ReportExportService() {
        this(new TextReportFormatter(), new JsonReportFormatter());
    }

    public ReportExportService(TextReportFormatter textFormatter, JsonReportFormatter jsonFormatter) {
        this.textFormatter = Objects.requireNonNull(textFormatter, "textFormatter must not be null");
        this.jsonFormatter = Objects.requireNonNull(jsonFormatter, "jsonFormatter must not be null");
    }

    /**
     * Formats the given analysis result into the specified format.
     *
     * @param result analysis result to format; must not be null
     * @param format target report format; must not be null
     * @return formatted report string
     */
    public String format(AnalysisResult result, ReportFormat format) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(format, "format must not be null");
        return switch (format) {
            case TEXT -> formatText(result);
            case JSON -> formatJson(result);
        };
    }

    /**
     * Formats the given analysis result to human-readable plain text.
     *
     * @param result analysis result to format; must not be null
     * @return formatted plain text report
     */
    public String formatText(AnalysisResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return textFormatter.format(result);
    }

    /**
     * Formats the given analysis result to deterministic JSON.
     *
     * @param result analysis result to format; must not be null
     * @return formatted JSON report string
     */
    public String formatJson(AnalysisResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return jsonFormatter.format(result);
    }

    /**
     * Safely exports the formatted report to the destination file.
     * Creates parent directories if necessary, overwrites any existing file,
     * and wraps any I/O errors into {@link ReportExportException}.
     *
     * @param result analysis result to export; must not be null
     * @param destination destination file path; must not be null
     * @param format target report format; must not be null
     * @throws ReportExportException if destination is invalid or writing fails
     */
    public void exportToFile(AnalysisResult result, Path destination, ReportFormat format) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(format, "format must not be null");

        if (Files.isDirectory(destination)) {
            throw new ReportExportException("Destination path is an existing directory: " + destination);
        }

        String content = format(result, format);

        try {
            Path parent = destination.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                destination,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            throw new ReportExportException("Failed to export report to " + destination + ": " + e.getMessage(), e);
        }
    }
}
