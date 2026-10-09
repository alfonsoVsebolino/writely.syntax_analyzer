package com.writely.syntax_analyzer.core.ingestion;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultSourceIngestionService Unit & Hardening Tests")
class DefaultSourceIngestionServiceTest {

    private DefaultSourceIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSourceIngestionService();
    }

    @Test
    @DisplayName("Default constructor configures exactly 10 MB maximum file size")
    void testDefaultConstructorConfigures10Mb() {
        assertEquals(10L * 1024L * 1024L, service.maxFileSizeBytes());
        assertEquals(DefaultSourceIngestionService.MAX_FILE_SIZE_BYTES, service.maxFileSizeBytes());
    }

    @Test
    @DisplayName("File exceeding 10 MB limit throws IngestionException with FILE_TOO_LARGE and clear message")
    void testFileExceeding10MbThrowsLarge(@TempDir Path tempDir) throws IOException {
        Path largeFile = tempDir.resolve("Oversized.java");
        long targetSize = (10L * 1024L * 1024L) + 1L; // 10 MB + 1 byte

        try (RandomAccessFile raf = new RandomAccessFile(largeFile.toFile(), "rw")) {
            raf.setLength(targetSize);
        }

        IngestionException ex = assertThrows(IngestionException.class,
            () -> service.ingestFile(largeFile));

        assertEquals(IngestionErrorCode.FILE_TOO_LARGE, ex.errorCode());
        assertTrue(ex.getMessage().contains("File exceeds 10 MB limit"),
            "Error message should mention exceeding 10 MB limit, was: " + ex.getMessage());
    }

    @Test
    @DisplayName("Valid file within 10 MB is ingested successfully")
    void testFileWithin10MbSucceeds(@TempDir Path tempDir) throws IOException {
        Path validFile = tempDir.resolve("Valid.py");
        String code = "def greet(name):\n    return f'Hello, {name}!'\n";
        Files.writeString(validFile, code, StandardCharsets.UTF_8);

        SourcePayload payload = service.ingestFile(validFile);
        assertNotNull(payload);
        assertEquals(code, payload.sourceText());
        assertEquals(Language.PYTHON, payload.language());
    }

    @Test
    @DisplayName("Binary file with null byte at beginning throws BINARY_FILE_DETECTED with explicit message")
    void testBinaryFileWithNullByteAtStart(@TempDir Path tempDir) throws IOException {
        Path binaryFile = tempDir.resolve("start_null.bin.py");
        byte[] content = new byte[]{0x00, 'p', 'r', 'i', 'n', 't', '(', ')'};
        Files.write(binaryFile, content);

        IngestionException ex = assertThrows(IngestionException.class,
            () -> service.ingestFile(binaryFile));

        assertEquals(IngestionErrorCode.BINARY_FILE_DETECTED, ex.errorCode());
        assertEquals("Binary file detected. Only UTF-8 plain text source files are supported.", ex.getMessage());
    }

    @Test
    @DisplayName("Binary file with null byte in middle of first 8KB throws BINARY_FILE_DETECTED")
    void testBinaryFileWithNullByteInFirst8Kb(@TempDir Path tempDir) throws IOException {
        Path binaryFile = tempDir.resolve("middle_null.java");
        byte[] content = "public class A { /* \0 null byte */ }".getBytes(StandardCharsets.UTF_8);
        Files.write(binaryFile, content);

        IngestionException ex = assertThrows(IngestionException.class,
            () -> service.ingestFile(binaryFile));

        assertEquals(IngestionErrorCode.BINARY_FILE_DETECTED, ex.errorCode());
        assertEquals("Binary file detected. Only UTF-8 plain text source files are supported.", ex.getMessage());
    }

    @Test
    @DisplayName("Binary file with null byte at byte offset 8191 (last byte of 8KB buffer) is detected")
    void testBinaryFileWithNullByteAt8KbBoundary(@TempDir Path tempDir) throws IOException {
        Path binaryFile = tempDir.resolve("boundary_null.cpp");
        byte[] buffer = new byte[8192];
        Arrays.fill(buffer, (byte) ' ');
        buffer[0] = '/';
        buffer[1] = '/';
        buffer[8191] = 0x00; // Null byte at boundary index 8191
        Files.write(binaryFile, buffer);

        IngestionException ex = assertThrows(IngestionException.class,
            () -> service.ingestFile(binaryFile));

        assertEquals(IngestionErrorCode.BINARY_FILE_DETECTED, ex.errorCode());
        assertEquals("Binary file detected. Only UTF-8 plain text source files are supported.", ex.getMessage());
    }

    @Test
    @DisplayName("Malformed UTF-8 sequence throws BINARY_FILE_DETECTED")
    void testMalformedUtf8ThrowsBinaryDetected(@TempDir Path tempDir) throws IOException {
        Path malformedFile = tempDir.resolve("malformed.cpp");
        byte[] invalidUtf8 = new byte[]{'i', 'n', 't', ' ', (byte) 0xC3, (byte) 0x28, ';'};
        Files.write(malformedFile, invalidUtf8);

        IngestionException ex = assertThrows(IngestionException.class,
            () -> service.ingestFile(malformedFile));

        assertEquals(IngestionErrorCode.BINARY_FILE_DETECTED, ex.errorCode());
        assertEquals("Binary file detected. Only UTF-8 plain text source files are supported.", ex.getMessage());
    }

    @Test
    @DisplayName("Custom max file size threshold enforces configured ceiling")
    void testCustomThresholdEnforcement(@TempDir Path tempDir) throws IOException {
        DefaultSourceIngestionService customService = new DefaultSourceIngestionService(50);
        Path testFile = tempDir.resolve("custom.py");
        Files.writeString(testFile, "x = 10\ny = 20\nz = 30\nmore_data = 40\nadditional = 50\n");

        IngestionException ex = assertThrows(IngestionException.class,
            () -> customService.ingestFile(testFile));
        assertEquals(IngestionErrorCode.FILE_TOO_LARGE, ex.errorCode());
    }

    @Test
    @DisplayName("IngestionException message-only constructor infers error code")
    void testIngestionExceptionConvenienceConstructors() {
        IngestionException exSize = new IngestionException("File exceeds 10 MB limit");
        assertEquals(IngestionErrorCode.FILE_TOO_LARGE, exSize.errorCode());
        assertEquals("File exceeds 10 MB limit", exSize.getMessage());

        IngestionException exBinary = new IngestionException("Binary file detected. Only UTF-8 plain text source files are supported.");
        assertEquals(IngestionErrorCode.BINARY_FILE_DETECTED, exBinary.errorCode());
        assertEquals("Binary file detected. Only UTF-8 plain text source files are supported.", exBinary.getMessage());
    }
}
