package com.writely.syntax_analyzer.core.ingestion;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Default implementation of {@link SourceIngestionService} providing input validation,
 * file safety checks, UTF-8 verification, line ending normalization, and language resolution.
 */
public class DefaultSourceIngestionService implements SourceIngestionService {

    public static final long MAX_FILE_SIZE_BYTES = SourceIngestionService.MAX_FILE_SIZE_BYTES;
    private static final int BINARY_SCAN_BUFFER_SIZE = 8 * 1024; // 8 KB
    private static final String UTF8_BOM = "\uFEFF";

    private final long maxFileSizeBytes;

    public DefaultSourceIngestionService() {
        this(MAX_FILE_SIZE_BYTES);
    }

    public DefaultSourceIngestionService(long maxFileSizeBytes) {
        if (maxFileSizeBytes < 0) {
            throw new IllegalArgumentException("maxFileSizeBytes must not be negative");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * Returns the configured maximum file size in bytes.
     */
    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    @Override
    public SourcePayload ingestSingleLine(String line, Language explicitLanguage) {
        if (line == null || line.isBlank()) {
            throw new IngestionException(IngestionErrorCode.EMPTY_SOURCE_INPUT,
                "Single line source input must not be empty or blank");
        }
        if (explicitLanguage == null) {
            throw new IngestionException(IngestionErrorCode.LANGUAGE_REQUIRED,
                "Explicit language selection is required for single line ingestion");
        }
        String normalized = normalizeLineBreaks(line);
        return SourcePayload.singleLine(normalized, explicitLanguage);
    }

    @Override
    public SourcePayload ingestSnippet(String snippet, Language explicitLanguage) {
        if (snippet == null || snippet.isBlank()) {
            throw new IngestionException(IngestionErrorCode.EMPTY_SOURCE_INPUT,
                "Code snippet source input must not be empty or blank");
        }
        if (explicitLanguage == null) {
            throw new IngestionException(IngestionErrorCode.LANGUAGE_REQUIRED,
                "Explicit language selection is required for snippet ingestion");
        }
        String normalized = normalizeLineBreaks(snippet);
        return SourcePayload.snippet(normalized, explicitLanguage);
    }

    @Override
    public SourcePayload ingestFile(Path filePath, Language explicitLanguage) {
        if (filePath == null) {
            throw new IngestionException(IngestionErrorCode.FILE_NOT_FOUND,
                "Source file path must not be null");
        }
        if (!Files.exists(filePath)) {
            throw new IngestionException(IngestionErrorCode.FILE_NOT_FOUND,
                "Specified file does not exist: " + filePath);
        }
        if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new IngestionException(IngestionErrorCode.FILE_UNREADABLE,
                "Specified file is not a regular readable file: " + filePath);
        }

        long fileSize;
        try {
            fileSize = readFileSize(filePath);
        } catch (IOException e) {
            throw new IngestionException(IngestionErrorCode.IO_ERROR,
                "Failed to read file metadata: " + filePath, e);
        }

        if (fileSize > maxFileSizeBytes) {
            throw new IngestionException(IngestionErrorCode.FILE_TOO_LARGE,
                "File size (" + fileSize + " bytes) exceeds maximum allowed limit of " + maxFileSizeBytes + " bytes: " + filePath);
        }

        String sourceName = Objects.toString(filePath.getFileName(), filePath.toString());

        Language resolvedLanguage = explicitLanguage;
        if (resolvedLanguage == null) {
            resolvedLanguage = Language.fromFileName(sourceName)
                .orElseThrow(() -> new IngestionException(IngestionErrorCode.LANGUAGE_REQUIRED,
                    "Explicit language selection is required for file with unrecognized extension: " + sourceName));
        }

        byte[] bytes;
        try {
            bytes = readFileBytes(filePath);
        } catch (IOException e) {
            throw new IngestionException(IngestionErrorCode.IO_ERROR,
                "Failed to read source file content: " + filePath, e);
        }

        int scanLength = Math.min(bytes.length, BINARY_SCAN_BUFFER_SIZE);
        for (int i = 0; i < scanLength; i++) {
            if (bytes[i] == 0) {
                throw new IngestionException(IngestionErrorCode.BINARY_FILE_DETECTED,
                    "Binary null byte detected in source file: " + filePath);
            }
        }

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        CharBuffer charBuffer;
        try {
            charBuffer = decoder.decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            throw new IngestionException(IngestionErrorCode.BINARY_FILE_DETECTED,
                "Malformed UTF-8 encoding or binary content detected in source file: " + filePath, e);
        }

        String content = charBuffer.toString();
        if (content.startsWith(UTF8_BOM)) {
            content = content.substring(UTF8_BOM.length());
        }

        if (content.isBlank()) {
            throw new IngestionException(IngestionErrorCode.EMPTY_SOURCE_INPUT,
                "Source file content is empty or contains only whitespace: " + filePath);
        }

        String normalized = normalizeLineBreaks(content);
        return SourcePayload.file(normalized, sourceName, resolvedLanguage);
    }

    long readFileSize(Path path) throws IOException {
        return Files.size(path);
    }

    byte[] readFileBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    private static String normalizeLineBreaks(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
