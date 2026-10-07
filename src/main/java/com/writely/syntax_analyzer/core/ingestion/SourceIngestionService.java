package com.writely.syntax_analyzer.core.ingestion;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;

import java.nio.file.Path;

/**
 * Service contract for multi-modal source code ingestion.
 * Normalizes inputs across single-line commands, multi-line snippets, and file uploads
 * into uniform {@link SourcePayload} objects before parser delegation.
 */
public interface SourceIngestionService {

    /**
     * Maximum allowed source file size in bytes (15 MB).
     */
    long MAX_FILE_SIZE_BYTES = 15L * 1024L * 1024L;

    /**
     * Ingests a single line of source code with an explicitly specified language.
     *
     * @param line             the source line text
     * @param explicitLanguage the target programming language (required)
     * @return the ingested source payload
     * @throws IngestionException if the input is empty or if explicitLanguage is null
     */
    SourcePayload ingestSingleLine(String line, Language explicitLanguage);

    /**
     * Ingests a multi-line code snippet with an explicitly specified language.
     *
     * @param snippet          the code snippet text
     * @param explicitLanguage the target programming language (required)
     * @return the ingested source payload
     * @throws IngestionException if the input is empty or if explicitLanguage is null
     */
    SourcePayload ingestSnippet(String snippet, Language explicitLanguage);

    /**
     * Ingests a local source file, optionally with an explicit language override.
     * If explicitLanguage is null, the language is inferred from the file extension.
     *
     * @param filePath         the path to the source file
     * @param explicitLanguage optional language override (may be null for extension inference)
     * @return the ingested source payload
     * @throws IngestionException if the file is invalid, missing, unreadable, binary, or exceeds size limits
     */
    SourcePayload ingestFile(Path filePath, Language explicitLanguage);

    /**
     * Ingests a local source file with language inferred from its file extension.
     *
     * @param filePath the path to the source file
     * @return the ingested source payload
     * @throws IngestionException if language cannot be inferred or file ingestion fails
     */
    default SourcePayload ingestFile(Path filePath) {
        return ingestFile(filePath, null);
    }
}
