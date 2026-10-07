package com.writely.syntax_analyzer.core.ingestion;

import com.writely.syntax_analyzer.domain.InputMode;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@DisplayName("Source Ingestion Service Tests")
class SourceIngestionServiceTest {

    private SourceIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new DefaultSourceIngestionService();
    }

    @Nested
    @DisplayName("Single Line Ingestion Tests")
    class SingleLineTests {

        @Test
        @DisplayName("Valid Java single line ingested with SINGLE_LINE mode and metadata")
        void testValidSingleLineJava() {
            String code = "int counter = 42;";
            SourcePayload payload = ingestionService.ingestSingleLine(code, Language.JAVA);

            assertNotNull(payload);
            assertEquals("int counter = 42;", payload.sourceText());
            assertEquals("<single-line>", payload.sourceName());
            assertEquals(Language.JAVA, payload.language());
            assertEquals(InputMode.SINGLE_LINE, payload.inputMode());
            assertEquals(17, payload.characterCount());
            assertEquals(1, payload.lineCount());
        }

        @Test
        @DisplayName("Valid Python single line ingested correctly")
        void testValidSingleLinePython() {
            String code = "print('Hello, Python!')";
            SourcePayload payload = ingestionService.ingestSingleLine(code, Language.PYTHON);

            assertNotNull(payload);
            assertEquals("print('Hello, Python!')", payload.sourceText());
            assertEquals(Language.PYTHON, payload.language());
            assertEquals(InputMode.SINGLE_LINE, payload.inputMode());
        }

        @Test
        @DisplayName("Valid C++ single line ingested correctly")
        void testValidSingleLineCpp() {
            String code = "std::cout << 100 << std::endl;";
            SourcePayload payload = ingestionService.ingestSingleLine(code, Language.CPP);

            assertNotNull(payload);
            assertEquals("std::cout << 100 << std::endl;", payload.sourceText());
            assertEquals(Language.CPP, payload.language());
            assertEquals(InputMode.SINGLE_LINE, payload.inputMode());
        }

        @Test
        @DisplayName("Single line normalizes CRLF and CR endings to LF")
        void testSingleLineNormalizesLineBreaks() {
            String codeWithCrLf = "int x = 1;\r\n";
            SourcePayload payloadCrLf = ingestionService.ingestSingleLine(codeWithCrLf, Language.JAVA);
            assertEquals("int x = 1;\n", payloadCrLf.sourceText());
            assertFalse(payloadCrLf.sourceText().contains("\r"));

            String codeWithCr = "int y = 2;\r";
            SourcePayload payloadCr = ingestionService.ingestSingleLine(codeWithCr, Language.JAVA);
            assertEquals("int y = 2;\n", payloadCr.sourceText());
            assertFalse(payloadCr.sourceText().contains("\r"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n", " \t \r\n "})
        @DisplayName("Empty or whitespace-only single line throws EMPTY_SOURCE_INPUT")
        void testEmptyOrBlankSingleLineThrows(String blankInput) {
            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestSingleLine(blankInput, Language.JAVA));

            assertEquals(IngestionErrorCode.EMPTY_SOURCE_INPUT, ex.errorCode());
            assertEquals(IngestionErrorCode.EMPTY_SOURCE_INPUT, ex.getErrorCode());
        }

        @Test
        @DisplayName("Single line with null explicit language throws LANGUAGE_REQUIRED")
        void testSingleLineNullLanguageThrows() {
            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestSingleLine("int x = 1;", null));

            assertEquals(IngestionErrorCode.LANGUAGE_REQUIRED, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("Code Snippet Ingestion Tests")
    class SnippetTests {

        @Test
        @DisplayName("Valid multi-line snippet ingested with CODE_SNIPPET mode and line counts")
        void testValidMultiLineSnippet() {
            String snippet = "public class Example {\n    public static void main(String[] args) {\n    }\n}";
            SourcePayload payload = ingestionService.ingestSnippet(snippet, Language.JAVA);

            assertNotNull(payload);
            assertEquals(snippet, payload.sourceText());
            assertEquals("<snippet>", payload.sourceName());
            assertEquals(Language.JAVA, payload.language());
            assertEquals(InputMode.CODE_SNIPPET, payload.inputMode());
            assertEquals(4, payload.lineCount());
        }

        @Test
        @DisplayName("Snippet normalizes CRLF and mixed line endings to LF")
        void testSnippetNormalizesCrLf() {
            String crlfSnippet = "def compute(a, b):\r\n    total = a + b\r\n    return total\r";
            SourcePayload payload = ingestionService.ingestSnippet(crlfSnippet, Language.PYTHON);

            String expected = "def compute(a, b):\n    total = a + b\n    return total\n";
            assertEquals(expected, payload.sourceText());
            assertFalse(payload.sourceText().contains("\r"));
            assertEquals(4, payload.lineCount());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\t", "\r\n\r\n", "  \n  \t  \r\n  "})
        @DisplayName("Empty or whitespace-only snippet throws EMPTY_SOURCE_INPUT")
        void testEmptyOrBlankSnippetThrows(String blankSnippet) {
            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestSnippet(blankSnippet, Language.PYTHON));

            assertEquals(IngestionErrorCode.EMPTY_SOURCE_INPUT, ex.errorCode());
        }

        @Test
        @DisplayName("Snippet with null explicit language throws LANGUAGE_REQUIRED")
        void testSnippetNullLanguageThrows() {
            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestSnippet("def foo():\n    pass", null));

            assertEquals(IngestionErrorCode.LANGUAGE_REQUIRED, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("File Ingestion Tests")
    class FileIngestionTests {

        @Test
        @DisplayName("Valid .java file ingested with inferred language and FILE_UPLOAD mode")
        void testValidJavaFile(@TempDir Path tempDir) throws IOException {
            Path file = tempDir.resolve("Calculator.java");
            String code = "public class Calculator {\n    int add(int a, int b) { return a + b; }\n}";
            Files.writeString(file, code, StandardCharsets.UTF_8);

            SourcePayload payload = ingestionService.ingestFile(file);

            assertNotNull(payload);
            assertEquals(code, payload.sourceText());
            assertEquals("Calculator.java", payload.sourceName());
            assertEquals(Language.JAVA, payload.language());
            assertEquals(InputMode.FILE_UPLOAD, payload.inputMode());
            assertEquals(3, payload.lineCount());
        }

        @Test
        @DisplayName("Valid .py file ingested with inferred language")
        void testValidPythonFile(@TempDir Path tempDir) throws IOException {
            Path file = tempDir.resolve("script.py");
            String code = "import sys\n\ndef main():\n    print('Running')\n";
            Files.writeString(file, code, StandardCharsets.UTF_8);

            SourcePayload payload = ingestionService.ingestFile(file);

            assertNotNull(payload);
            assertEquals(code, payload.sourceText());
            assertEquals("script.py", payload.sourceName());
            assertEquals(Language.PYTHON, payload.language());
            assertEquals(InputMode.FILE_UPLOAD, payload.inputMode());
        }

        @Test
        @DisplayName("Valid .cpp and C++ family files (.cxx, .cc, .hpp, .h) ingested with inferred CPP language")
        void testValidCppFiles(@TempDir Path tempDir) throws IOException {
            Path cppFile = tempDir.resolve("app.cpp");
            Files.writeString(cppFile, "#include <iostream>\nint main() { return 0; }", StandardCharsets.UTF_8);

            SourcePayload cppPayload = ingestionService.ingestFile(cppFile);
            assertEquals(Language.CPP, cppPayload.language());
            assertEquals("app.cpp", cppPayload.sourceName());

            Path hppFile = tempDir.resolve("math_util.hpp");
            Files.writeString(hppFile, "#pragma once\nint square(int x);", StandardCharsets.UTF_8);
            SourcePayload hppPayload = ingestionService.ingestFile(hppFile);
            assertEquals(Language.CPP, hppPayload.language());

            Path ccFile = tempDir.resolve("logic.cc");
            Files.writeString(ccFile, "int logic() { return 1; }", StandardCharsets.UTF_8);
            assertEquals(Language.CPP, ingestionService.ingestFile(ccFile).language());

            Path cxxFile = tempDir.resolve("core.cxx");
            Files.writeString(cxxFile, "int core() { return 2; }", StandardCharsets.UTF_8);
            assertEquals(Language.CPP, ingestionService.ingestFile(cxxFile).language());

            Path hFile = tempDir.resolve("header.h");
            Files.writeString(hFile, "void init();", StandardCharsets.UTF_8);
            assertEquals(Language.CPP, ingestionService.ingestFile(hFile).language());
        }

        @Test
        @DisplayName("Explicit language overrides file extension inference")
        void testExplicitLanguageOverride(@TempDir Path tempDir) throws IOException {
            // File with .txt extension but explicitly selected as JAVA
            Path txtFile = tempDir.resolve("CodeSnippet.txt");
            Files.writeString(txtFile, "class CodeSnippet {}", StandardCharsets.UTF_8);

            SourcePayload payload = ingestionService.ingestFile(txtFile, Language.JAVA);
            assertEquals(Language.JAVA, payload.language());
            assertEquals("CodeSnippet.txt", payload.sourceName());

            // File with .py extension but explicitly selected as CPP
            Path pyFile = tempDir.resolve("mixed.py");
            Files.writeString(pyFile, "int main() { return 0; }", StandardCharsets.UTF_8);

            SourcePayload cppPayload = ingestionService.ingestFile(pyFile, Language.CPP);
            assertEquals(Language.CPP, cppPayload.language());
        }

        @Test
        @DisplayName("Language required when file extension is unrecognized and explicit language is null")
        void testLanguageRequiredWhenExtensionUnrecognized(@TempDir Path tempDir) throws IOException {
            Path txtFile = tempDir.resolve("notes.txt");
            Files.writeString(txtFile, "Some unstructured notes", StandardCharsets.UTF_8);

            IngestionException exTxt = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(txtFile, null));
            assertEquals(IngestionErrorCode.LANGUAGE_REQUIRED, exTxt.errorCode());

            Path noExtFile = tempDir.resolve("Dockerfile");
            Files.writeString(noExtFile, "FROM eclipse-temurin:21", StandardCharsets.UTF_8);

            IngestionException exNoExt = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(noExtFile, null));
            assertEquals(IngestionErrorCode.LANGUAGE_REQUIRED, exNoExt.errorCode());
        }

        @Test
        @DisplayName("File not found throws FILE_NOT_FOUND")
        void testFileNotFound(@TempDir Path tempDir) {
            Path nonexistent = tempDir.resolve("does_not_exist.java");

            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(nonexistent, Language.JAVA));
            assertEquals(IngestionErrorCode.FILE_NOT_FOUND, ex.errorCode());

            IngestionException nullEx = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(null, Language.JAVA));
            assertEquals(IngestionErrorCode.FILE_NOT_FOUND, nullEx.errorCode());
        }

        @Test
        @DisplayName("Directory path throws FILE_UNREADABLE")
        void testDirectoryPathThrowsUnreadable(@TempDir Path tempDir) {
            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(tempDir, Language.JAVA));
            assertEquals(IngestionErrorCode.FILE_UNREADABLE, ex.errorCode());
        }

        @Test
        @DisplayName("Unreadable file permissions throw FILE_UNREADABLE")
        void testUnreadablePermissionsThrows(@TempDir Path tempDir) throws IOException {
            Path file = tempDir.resolve("secret.java");
            Files.writeString(file, "class Secret {}", StandardCharsets.UTF_8);

            boolean revoked = file.toFile().setReadable(false);
            assumeFalse(!revoked || Files.isReadable(file), "Filesystem does not support revoking read permissions");

            try {
                IngestionException ex = assertThrows(IngestionException.class,
                    () -> ingestionService.ingestFile(file, Language.JAVA));
                assertEquals(IngestionErrorCode.FILE_UNREADABLE, ex.errorCode());
            } finally {
                file.toFile().setReadable(true);
            }
        }

        @Test
        @DisplayName("Binary file with null byte in initial 8 KB throws BINARY_FILE_DETECTED")
        void testBinaryFileWithNullByteDetected(@TempDir Path tempDir) throws IOException {
            Path binaryFile = tempDir.resolve("binary.java");
            byte[] binaryContent = new byte[]{0x63, 0x6C, 0x61, 0x73, 0x73, 0x00, 0x20, 0x41}; // "class\0 A"
            Files.write(binaryFile, binaryContent);

            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(binaryFile));
            assertEquals(IngestionErrorCode.BINARY_FILE_DETECTED, ex.errorCode());
        }

        @Test
        @DisplayName("Binary file starting with null byte throws BINARY_FILE_DETECTED")
        void testBinaryFileStartingWithNullByte(@TempDir Path tempDir) throws IOException {
            Path binaryFile = tempDir.resolve("zero_start.py");
            byte[] binaryContent = new byte[]{0x00, 0x70, 0x72, 0x69, 0x6E, 0x74};
            Files.write(binaryFile, binaryContent);

            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(binaryFile));
            assertEquals(IngestionErrorCode.BINARY_FILE_DETECTED, ex.errorCode());
        }

        @Test
        @DisplayName("Malformed UTF-8 sequences throw BINARY_FILE_DETECTED")
        void testMalformedUtf8ThrowsBinaryDetected(@TempDir Path tempDir) throws IOException {
            Path malformedFile = tempDir.resolve("malformed.cpp");
            // Invalid UTF-8 sequence: 0xC3 followed by an ASCII character 0x28 (not a valid continuation byte)
            byte[] invalidUtf8 = new byte[]{'i', 'n', 't', ' ', (byte) 0xC3, (byte) 0x28, ';'};
            Files.write(malformedFile, invalidUtf8);

            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(malformedFile));
            assertEquals(IngestionErrorCode.BINARY_FILE_DETECTED, ex.errorCode());
        }

        @Test
        @DisplayName("File exceeding 15 MB limit throws FILE_TOO_LARGE")
        void testFileExceeding15MbThrowsLarge(@TempDir Path tempDir) throws IOException {
            Path largeFile = tempDir.resolve("huge.java");
            // Create a sparse file of 15 MB + 1 byte
            try (RandomAccessFile raf = new RandomAccessFile(largeFile.toFile(), "rw")) {
                raf.setLength(DefaultSourceIngestionService.MAX_FILE_SIZE_BYTES + 1);
            }

            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(largeFile));
            assertEquals(IngestionErrorCode.FILE_TOO_LARGE, ex.errorCode());
        }

        @Test
        @DisplayName("Custom max file size threshold enforces boundary precisely")
        void testCustomFileThreshold(@TempDir Path tempDir) throws IOException {
            SourceIngestionService customService = new DefaultSourceIngestionService(100);

            Path file101 = tempDir.resolve("file101.py");
            Files.writeString(file101, "a".repeat(101), StandardCharsets.UTF_8);

            IngestionException ex = assertThrows(IngestionException.class,
                () -> customService.ingestFile(file101));
            assertEquals(IngestionErrorCode.FILE_TOO_LARGE, ex.errorCode());

            Path file100 = tempDir.resolve("file100.py");
            Files.writeString(file100, "a".repeat(100), StandardCharsets.UTF_8);

            SourcePayload payload = customService.ingestFile(file100);
            assertNotNull(payload);
            assertEquals(100, payload.characterCount());
        }

        @Test
        @DisplayName("Empty (0-byte) source file throws EMPTY_SOURCE_INPUT")
        void testEmptyFileThrows(@TempDir Path tempDir) throws IOException {
            Path emptyFile = tempDir.resolve("Empty.java");
            Files.createFile(emptyFile);

            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(emptyFile));
            assertEquals(IngestionErrorCode.EMPTY_SOURCE_INPUT, ex.errorCode());
        }

        @Test
        @DisplayName("Whitespace-only file throws EMPTY_SOURCE_INPUT")
        void testWhitespaceOnlyFileThrows(@TempDir Path tempDir) throws IOException {
            Path whitespaceFile = tempDir.resolve("whitespace.py");
            Files.writeString(whitespaceFile, "   \n\t\r\n   ", StandardCharsets.UTF_8);

            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(whitespaceFile));
            assertEquals(IngestionErrorCode.EMPTY_SOURCE_INPUT, ex.errorCode());
        }

        @Test
        @DisplayName("UTF-8 BOM is stripped and code is ingested cleanly")
        void testUtf8BomHandledCorrectly(@TempDir Path tempDir) throws IOException {
            Path bomFile = tempDir.resolve("WithBom.java");
            byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] code = "class WithBom {}".getBytes(StandardCharsets.UTF_8);
            byte[] total = new byte[bom.length + code.length];
            System.arraycopy(bom, 0, total, 0, bom.length);
            System.arraycopy(code, 0, total, bom.length, code.length);

            Files.write(bomFile, total);

            SourcePayload payload = ingestionService.ingestFile(bomFile);
            assertEquals("class WithBom {}", payload.sourceText());
            assertEquals(Language.JAVA, payload.language());

            // File with ONLY UTF-8 BOM should throw EMPTY_SOURCE_INPUT
            Path bomOnlyFile = tempDir.resolve("BomOnly.java");
            Files.write(bomOnlyFile, bom);

            IngestionException ex = assertThrows(IngestionException.class,
                () -> ingestionService.ingestFile(bomOnlyFile));
            assertEquals(IngestionErrorCode.EMPTY_SOURCE_INPUT, ex.errorCode());
        }

        @Test
        @DisplayName("File with CRLF line breaks is normalized to LF")
        void testFileCrLfNormalized(@TempDir Path tempDir) throws IOException {
            Path file = tempDir.resolve("LineBreaks.cpp");
            Files.writeString(file, "#include <iostream>\r\nint main() {\r\n    return 0;\r\n}\r\n", StandardCharsets.UTF_8);

            SourcePayload payload = ingestionService.ingestFile(file);
            assertFalse(payload.sourceText().contains("\r"));
            assertEquals("#include <iostream>\nint main() {\n    return 0;\n}\n", payload.sourceText());
        }

        @Test
        @DisplayName("I/O error reading file size throws IO_ERROR with cause")
        void testIoErrorReadingFileSize(@TempDir Path tempDir) throws IOException {
            Path file = tempDir.resolve("io_size.java");
            Files.writeString(file, "class IoSize {}", StandardCharsets.UTF_8);

            DefaultSourceIngestionService faultyService = new DefaultSourceIngestionService() {
                @Override
                long readFileSize(Path path) throws IOException {
                    throw new IOException("Simulated size I/O failure");
                }
            };

            IngestionException ex = assertThrows(IngestionException.class,
                () -> faultyService.ingestFile(file));
            assertEquals(IngestionErrorCode.IO_ERROR, ex.errorCode());
            assertNotNull(ex.getCause());
            assertTrue(ex.getCause() instanceof IOException);
        }

        @Test
        @DisplayName("I/O error reading file bytes throws IO_ERROR with cause")
        void testIoErrorReadingFileBytes(@TempDir Path tempDir) throws IOException {
            Path file = tempDir.resolve("io_bytes.java");
            Files.writeString(file, "class IoBytes {}", StandardCharsets.UTF_8);

            DefaultSourceIngestionService faultyService = new DefaultSourceIngestionService() {
                @Override
                byte[] readFileBytes(Path path) throws IOException {
                    throw new IOException("Simulated read bytes I/O failure");
                }
            };

            IngestionException ex = assertThrows(IngestionException.class,
                () -> faultyService.ingestFile(file));
            assertEquals(IngestionErrorCode.IO_ERROR, ex.errorCode());
            assertNotNull(ex.getCause());
            assertTrue(ex.getCause() instanceof IOException);
        }
    }

    @Nested
    @DisplayName("Exception and Error Code Contract Tests")
    class ExceptionAndContractTests {

        @Test
        @DisplayName("IngestionErrorCode contains all 7 canonical error codes and descriptions")
        void testIngestionErrorCodes() {
            assertEquals(7, IngestionErrorCode.values().length);
            for (IngestionErrorCode code : IngestionErrorCode.values()) {
                assertNotNull(code.description());
                assertFalse(code.description().isBlank());
            }
        }

        @Test
        @DisplayName("IngestionException carries error code, message, cause, and rejects null error code")
        void testIngestionExceptionInvariants() {
            IngestionException ex1 = new IngestionException(IngestionErrorCode.EMPTY_SOURCE_INPUT);
            assertEquals(IngestionErrorCode.EMPTY_SOURCE_INPUT, ex1.errorCode());
            assertEquals(IngestionErrorCode.EMPTY_SOURCE_INPUT, ex1.getErrorCode());
            assertEquals(IngestionErrorCode.EMPTY_SOURCE_INPUT.description(), ex1.getMessage());
            assertNull(ex1.getCause());

            Throwable cause = new IOException("Disk failure");
            IngestionException ex2 = new IngestionException(IngestionErrorCode.IO_ERROR, "Disk error occurred", cause);
            assertEquals(IngestionErrorCode.IO_ERROR, ex2.errorCode());
            assertEquals("Disk error occurred", ex2.getMessage());
            assertSame(cause, ex2.getCause());

            assertThrows(NullPointerException.class, () -> new IngestionException(null, "msg"));
            assertThrows(NullPointerException.class, () -> new IngestionException(null, "msg", cause));
        }

        @Test
        @DisplayName("DefaultSourceIngestionService rejects negative threshold")
        void testRejectsNegativeThreshold() {
            assertThrows(IllegalArgumentException.class, () -> new DefaultSourceIngestionService(-1));
            DefaultSourceIngestionService service = new DefaultSourceIngestionService();
            assertEquals(DefaultSourceIngestionService.MAX_FILE_SIZE_BYTES, service.maxFileSizeBytes());
        }
    }
}
