package com.writely.syntax_analyzer.core.report;

import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.DiagnosticSummary;
import com.writely.syntax_analyzer.domain.InputMode;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@DisplayName("Report Export Service Tests")
class ReportExportServiceTest {

    private ReportExportService exportService;
    private TextReportFormatter textFormatter;
    private JsonReportFormatter jsonFormatter;

    @BeforeEach
    void setUp() {
        textFormatter = new TextReportFormatter();
        jsonFormatter = new JsonReportFormatter();
        exportService = new ReportExportService(textFormatter, jsonFormatter);
    }

    private AnalysisResult createCleanPassingResult() {
        String code = "x = 10\nif (x > 5):\n    print(\"Value is valid\")";
        SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);

        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            tokens.add(Token.of(TokenType.IDENTIFIER, "tok" + i, SourceSpan.point(SourceLocation.of(1, 1, 0))));
        }

        return AnalysisResult.of(payload, tokens, List.of());
    }

    private AnalysisResult createCodeWithErrorsResult(String sourceName) {
        String code = "x = 10\nif (x > 5\n    print(\"Value is valid)\ny = 20 + * 5";
        SourcePayload payload = SourcePayload.of(code, sourceName, Language.PYTHON, InputMode.FILE_UPLOAD);

        List<Diagnostic> diagnostics = List.of(
            Diagnostic.of(
                CheckCategory.DELIMITER_MATCH,
                Severity.ERROR,
                SourceLocation.of(2, 10, 16),
                "Unclosed parenthesis '('. Expected ')' before line end.",
                "DELIM_001"
            ),
            Diagnostic.of(
                CheckCategory.LITERAL_SYNTAX,
                Severity.ERROR,
                SourceLocation.of(3, 11, 38),
                "Unclosed string literal. Missing matching double quote '\"'.",
                "LIT_001"
            ),
            Diagnostic.of(
                CheckCategory.OPERATOR_SYNTAX,
                Severity.ERROR,
                SourceLocation.of(4, 8, 59),
                "Consecutive binary operators '+ *' without an operand in between.",
                "OP_001"
            )
        );

        return AnalysisResult.of(payload, List.of(), diagnostics);
    }

    @Nested
    @DisplayName("Plain Text Report Formatting Tests")
    class TextFormattingTests {

        @Test
        @DisplayName("Clean passing code formats matching Section 3 specification")
        void testCleanPassingCodeTextFormat() {
            AnalysisResult result = createCleanPassingResult();

            String report = exportService.formatText(result);
            assertNotNull(report);

            String expected =
                "SYNTACTICAL ANALYSIS REPORT\n" +
                "Status: PASSED (0 Syntax Errors Found)\n" +
                "Total Lines Analyzed: 3\n" +
                "\n" +
                "SUMMARY:\n" +
                "- Total Lines Checked: 3\n" +
                "- Valid Lines: 3\n" +
                "- Flagged Lines: 0\n" +
                "- Action Required: None. All syntax rules passed.\n";

            assertEquals(expected, report);
            assertFalse(report.contains("SYNTAX ERROR DETAILS:"));
            assertFalse(report.contains("Source:"));
        }

        @Test
        @DisplayName("Clean passing code with real source name includes Source metadata")
        void testCleanPassingCodeWithSourceName() {
            String code = "int x = 42;\nreturn x;\n";
            SourcePayload payload = SourcePayload.file(code, "Calculator.java", Language.JAVA);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of());

            String report = exportService.formatText(result);

            assertTrue(report.contains("Source: Calculator.java"));
            assertTrue(report.contains("Total Lines Analyzed: 3"));
            assertTrue(report.contains("Status: PASSED (0 Syntax Errors Found)"));
        }

        @Test
        @DisplayName("Code with errors formats matching Section 3 specification")
        void testCodeWithErrorsTextFormat() {
            AnalysisResult result = createCodeWithErrorsResult("");

            String report = exportService.formatText(result);
            assertNotNull(report);

            String expected =
                "SYNTACTICAL ANALYSIS REPORT\n" +
                "Status: FAILED (3 Syntax Errors Detected)\n" +
                "Total Lines Analyzed: 4\n" +
                "\n" +
                "SYNTAX ERROR DETAILS:\n" +
                "[ERROR 1] Line 2: if (x > 5\n" +
                "- Category: Delimiter Match\n" +
                "- Details: Unclosed parenthesis '('. Expected ')' before line end.\n" +
                "\n" +
                "[ERROR 2] Line 3:     print(\"Value is valid)\n" +
                "- Category: Literal Syntax\n" +
                "- Details: Unclosed string literal. Missing matching double quote '\"'.\n" +
                "\n" +
                "[ERROR 3] Line 4: y = 20 + * 5\n" +
                "- Category: Operator Syntax\n" +
                "- Details: Consecutive binary operators '+ *' without an operand in between.\n" +
                "\n" +
                "SUMMARY:\n" +
                "- Total Lines Checked: 4\n" +
                "- Valid Lines: 1\n" +
                "- Flagged Lines: 3 (Lines 2, 3, 4)\n" +
                "- Action Required: Correct highlighted syntax errors above.\n";

            assertEquals(expected, report);
        }

        @Test
        @DisplayName("Code with errors and file name includes Source metadata")
        void testCodeWithErrorsAndFileName() {
            AnalysisResult result = createCodeWithErrorsResult("script.py");

            String report = exportService.formatText(result);

            assertTrue(report.contains("Source: script.py"));
            assertTrue(report.contains("Status: FAILED (3 Syntax Errors Detected)"));
            assertTrue(report.contains("Total Lines Analyzed: 4"));
            assertTrue(report.contains("[ERROR 1] Line 2: if (x > 5"));
        }

        @Test
        @DisplayName("Facade format method delegates correctly for ReportFormat.TEXT")
        void testFacadeFormatText() {
            AnalysisResult result = createCleanPassingResult();
            assertEquals(exportService.formatText(result), exportService.format(result, ReportFormat.TEXT));
        }

        @Test
        @DisplayName("Error on empty line formats Line without trailing space")
        void testErrorOnEmptyLine() {
            String code = "\n";
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            Diagnostic d = Diagnostic.of(CheckCategory.STATEMENT_TERMINATOR, Severity.ERROR, SourceLocation.of(1, 1, 0), "Missing token", "ERR_1");
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of(d));

            String report = exportService.formatText(result);
            assertTrue(report.contains("[ERROR 1] Line 1:\n"));
        }

        @Test
        @DisplayName("Empty source text produces 0 total lines and clean summary")
        void testEmptySourceTextReport() {
            SourcePayload payload = SourcePayload.snippet("", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of());

            String report = exportService.formatText(result);
            assertTrue(report.contains("Total Lines Analyzed: 0"));
            assertTrue(report.contains("- Total Lines Checked: 0"));
            assertTrue(report.contains("- Valid Lines: 0"));
            assertTrue(report.contains("- Flagged Lines: 0"));
            assertTrue(report.contains("- Action Required: None. All syntax rules passed."));
        }
    }

    @Nested
    @DisplayName("JSON Report Formatting Tests")
    class JsonFormattingTests {

        @Test
        @DisplayName("Passing result generates valid JSON structure with empty diagnostics array")
        void testPassingResultJsonStructure() {
            AnalysisResult result = createCleanPassingResult();

            String json = exportService.formatJson(result);
            assertNotNull(json);

            assertTrue(json.contains("\"language\": \"PYTHON\""));
            assertTrue(json.contains("\"inputMode\": \"CODE_SNIPPET\""));
            assertTrue(json.contains("\"sourceName\": \"<snippet>\""));
            assertTrue(json.contains("\"status\": \"PASSED\""));
            assertTrue(json.contains("\"totalLines\": 3"));
            assertTrue(json.contains("\"totalTokens\": 14"));
            assertTrue(json.contains("\"errorCount\": 0"));
            assertTrue(json.contains("\"warningCount\": 0"));
            assertTrue(json.contains("\"flaggedLines\": 0"));
            assertTrue(json.contains("\"validLines\": 3"));
            assertTrue(json.contains("\"categoryCounts\": {"));
            assertTrue(json.contains("\"DELIMITER_MATCH\": 0"));
            assertTrue(json.contains("\"LITERAL_SYNTAX\": 0"));
            assertTrue(json.contains("\"STATEMENT_TERMINATOR\": 0"));
            assertTrue(json.contains("\"OPERATOR_SYNTAX\": 0"));
            assertTrue(json.contains("\"CONTROL_HEADER\": 0"));
            assertTrue(json.contains("\"IDENTIFIER_NAMING\": 0"));
            assertTrue(json.contains("\"diagnostics\": []"));

            // Check balanced braces and brackets
            assertEquals(countOccurrences(json, '{'), countOccurrences(json, '}'));
            assertEquals(countOccurrences(json, '['), countOccurrences(json, ']'));
        }

        @Test
        @DisplayName("Error result generates valid JSON structure with diagnostic details")
        void testErrorResultJsonStructure() {
            AnalysisResult result = createCodeWithErrorsResult("app.py");

            String json = exportService.formatJson(result);
            assertNotNull(json);

            assertTrue(json.contains("\"language\": \"PYTHON\""));
            assertTrue(json.contains("\"sourceName\": \"app.py\""));
            assertTrue(json.contains("\"status\": \"FAILED_SYNTAX_ERRORS\""));
            assertTrue(json.contains("\"errorCount\": 3"));
            assertTrue(json.contains("\"flaggedLines\": 3"));
            assertTrue(json.contains("\"validLines\": 1"));

            // Diagnostic entries
            assertTrue(json.contains("\"code\": \"DELIM_001\""));
            assertTrue(json.contains("\"category\": \"DELIMITER_MATCH\""));
            assertTrue(json.contains("\"severity\": \"ERROR\""));
            assertTrue(json.contains("\"line\": 2"));
            assertTrue(json.contains("\"column\": 10"));

            assertTrue(json.contains("\"code\": \"LIT_001\""));
            assertTrue(json.contains("\"category\": \"LITERAL_SYNTAX\""));
            // Double quote escaping in message
            assertTrue(json.contains("Missing matching double quote '\\\"'."));

            assertTrue(json.contains("\"code\": \"OP_001\""));
            assertTrue(json.contains("\"category\": \"OPERATOR_SYNTAX\""));

            // Check balanced braces and brackets
            assertEquals(countOccurrences(json, '{'), countOccurrences(json, '}'));
            assertEquals(countOccurrences(json, '['), countOccurrences(json, ']'));
        }

        @Test
        @DisplayName("Compact JSON option generates single-line JSON without line breaks")
        void testCompactJsonFormatting() {
            AnalysisResult result = createCleanPassingResult();

            String compactJson = jsonFormatter.format(result, false);
            assertNotNull(compactJson);

            assertFalse(compactJson.contains("\n"));
            assertTrue(compactJson.startsWith("{\"language\":\"PYTHON\""));
            assertTrue(compactJson.endsWith("}"));
        }

        @Test
        @DisplayName("String escaping utility properly handles quotes, backslashes, tabs, newlines, and control chars")
        void testStringEscaping() {
            assertEquals("", JsonReportFormatter.escapeString(null));
            assertEquals("", JsonReportFormatter.escapeString(""));
            assertEquals("hello", JsonReportFormatter.escapeString("hello"));
            assertEquals("hello \\\"world\\\"", JsonReportFormatter.escapeString("hello \"world\""));
            assertEquals("path\\\\to\\\\file", JsonReportFormatter.escapeString("path\\to\\file"));
            assertEquals("line1\\nline2", JsonReportFormatter.escapeString("line1\nline2"));
            assertEquals("col1\\tcol2", JsonReportFormatter.escapeString("col1\tcol2"));
            assertEquals("cr\\rhere", JsonReportFormatter.escapeString("cr\rhere"));
            assertEquals("bell\\bform\\f", JsonReportFormatter.escapeString("bell\bform\f"));
            assertEquals("\\u0000", JsonReportFormatter.escapeString("\u0000"));
            assertEquals("\\u001f", JsonReportFormatter.escapeString("\u001F"));
        }

        @Test
        @DisplayName("Diagnostic message with backslashes and control chars is properly escaped in JSON output")
        void testDiagnosticMessageWithSpecialCharacters() {
            String code = "val = 1;";
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            Diagnostic d = Diagnostic.of(
                CheckCategory.LITERAL_SYNTAX,
                Severity.ERROR,
                SourceLocation.of(1, 1, 0),
                "Escapes: \\ \" \n \r \t",
                "ESC_001"
            );
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of(d));

            String json = exportService.formatJson(result);
            assertTrue(json.contains("\"message\": \"Escapes: \\\\ \\\" \\n \\r \\t\""));
        }

        @Test
        @DisplayName("Facade format method delegates correctly for ReportFormat.JSON")
        void testFacadeFormatJson() {
            AnalysisResult result = createCleanPassingResult();
            assertEquals(exportService.formatJson(result), exportService.format(result, ReportFormat.JSON));
        }

        private int countOccurrences(String str, char ch) {
            int count = 0;
            for (int i = 0; i < str.length(); i++) {
                if (str.charAt(i) == ch) {
                    count++;
                }
            }
            return count;
        }
    }

    @Nested
    @DisplayName("File Export Tests")
    class FileExportTests {

        @Test
        @DisplayName("Export plain text report creates file on disk with exact content")
        void testExportTextFileSuccess(@TempDir Path tempDir) throws IOException {
            AnalysisResult result = createCleanPassingResult();
            Path dest = tempDir.resolve("report.txt");

            exportService.exportToFile(result, dest, ReportFormat.TEXT);

            assertTrue(Files.exists(dest));
            String fileContent = Files.readString(dest);
            assertEquals(exportService.formatText(result), fileContent);
        }

        @Test
        @DisplayName("Export JSON report creates file on disk with exact content")
        void testExportJsonFileSuccess(@TempDir Path tempDir) throws IOException {
            AnalysisResult result = createCodeWithErrorsResult("app.py");
            Path dest = tempDir.resolve("report.json");

            exportService.exportToFile(result, dest, ReportFormat.JSON);

            assertTrue(Files.exists(dest));
            String fileContent = Files.readString(dest);
            assertEquals(exportService.formatJson(result), fileContent);
        }

        @Test
        @DisplayName("Export creates intermediate directories when destination parent does not exist")
        void testExportCreatesIntermediateDirectories(@TempDir Path tempDir) throws IOException {
            AnalysisResult result = createCleanPassingResult();
            Path nestedDest = tempDir.resolve("sub1").resolve("sub2").resolve("report.txt");

            assertFalse(Files.exists(nestedDest.getParent()));

            exportService.exportToFile(result, nestedDest, ReportFormat.TEXT);

            assertTrue(Files.exists(nestedDest));
            assertEquals(exportService.formatText(result), Files.readString(nestedDest));
        }

        @Test
        @DisplayName("Export overwrites existing destination file")
        void testExportOverwritesExistingFile(@TempDir Path tempDir) throws IOException {
            Path file = tempDir.resolve("existing_report.txt");
            Files.writeString(file, "OLD STALE CONTENT");

            AnalysisResult result = createCleanPassingResult();
            exportService.exportToFile(result, file, ReportFormat.TEXT);

            String updated = Files.readString(file);
            assertEquals(exportService.formatText(result), updated);
            assertFalse(updated.contains("OLD STALE CONTENT"));
        }

        @Test
        @DisplayName("Export to existing directory path throws ReportExportException")
        void testExportToDirectoryThrows(@TempDir Path tempDir) {
            AnalysisResult result = createCleanPassingResult();

            ReportExportException ex = assertThrows(ReportExportException.class,
                () -> exportService.exportToFile(result, tempDir, ReportFormat.TEXT));

            assertTrue(ex.getMessage().contains("Destination path is an existing directory"));
        }

        @Test
        @DisplayName("Export to read-only directory throws ReportExportException")
        void testExportToReadOnlyDirectoryThrows(@TempDir Path tempDir) throws IOException {
            Path readOnlyDir = tempDir.resolve("readonly");
            Files.createDirectories(readOnlyDir);

            boolean revoked = readOnlyDir.toFile().setWritable(false);
            assumeFalse(!revoked || Files.isWritable(readOnlyDir), "Filesystem does not support revoking write permissions");

            try {
                Path dest = readOnlyDir.resolve("report.txt");
                AnalysisResult result = createCleanPassingResult();

                ReportExportException ex = assertThrows(ReportExportException.class,
                    () -> exportService.exportToFile(result, dest, ReportFormat.TEXT));

                assertNotNull(ex.getMessage());
                assertTrue(ex.getMessage().contains("Failed to export report to"));
            } finally {
                readOnlyDir.toFile().setWritable(true);
            }
        }
    }

    @Nested
    @DisplayName("Invariants and Argument Validation Tests")
    class ValidationAndInvariantTests {

        @Test
        @DisplayName("Null arguments throw NullPointerException")
        void testNullArgumentValidations(@TempDir Path tempDir) {
            AnalysisResult result = createCleanPassingResult();
            Path path = tempDir.resolve("dummy.txt");

            assertThrows(NullPointerException.class, () -> exportService.format(null, ReportFormat.TEXT));
            assertThrows(NullPointerException.class, () -> exportService.format(result, null));
            assertThrows(NullPointerException.class, () -> exportService.formatText(null));
            assertThrows(NullPointerException.class, () -> exportService.formatJson(null));
            assertThrows(NullPointerException.class, () -> exportService.exportToFile(null, path, ReportFormat.TEXT));
            assertThrows(NullPointerException.class, () -> exportService.exportToFile(result, null, ReportFormat.TEXT));
            assertThrows(NullPointerException.class, () -> exportService.exportToFile(result, path, null));

            assertThrows(NullPointerException.class, () -> new ReportExportService(null, jsonFormatter));
            assertThrows(NullPointerException.class, () -> new ReportExportService(textFormatter, null));
        }

        @ParameterizedTest
        @EnumSource(ReportFormat.class)
        @DisplayName("ReportFormat enum contains valid metadata for each format")
        void testReportFormatEnum(ReportFormat format) {
            assertNotNull(format.extension());
            assertFalse(format.extension().isBlank());
            assertNotNull(format.mimeType());
            assertFalse(format.mimeType().isBlank());
            assertNotNull(format.displayName());
            assertFalse(format.displayName().isBlank());
        }

        @Test
        @DisplayName("ReportExportException constructors preserve message and cause")
        void testReportExportExceptionConstructors() {
            ReportExportException ex1 = new ReportExportException("error message");
            assertEquals("error message", ex1.getMessage());
            assertNull(ex1.getCause());

            Throwable cause = new IOException("io failure");
            ReportExportException ex2 = new ReportExportException("wrapped error", cause);
            assertEquals("wrapped error", ex2.getMessage());
            assertSame(cause, ex2.getCause());

            ReportExportException ex3 = new ReportExportException(cause);
            assertEquals(cause.toString(), ex3.getMessage());
            assertSame(cause, ex3.getCause());
        }

        @Test
        @DisplayName("Default ReportExportService constructor instantiates successfully")
        void testDefaultConstructor() {
            ReportExportService service = new ReportExportService();
            assertNotNull(service);
            AnalysisResult result = createCleanPassingResult();
            assertNotNull(service.formatText(result));
            assertNotNull(service.formatJson(result));
        }
    }
}
