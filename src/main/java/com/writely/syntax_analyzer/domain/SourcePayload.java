package com.writely.syntax_analyzer.domain;

import java.util.Objects;

/**
 * Encapsulates the ingested source input along with its metadata and origin.
 */
public record SourcePayload(
    String sourceText,
    String sourceName,
    Language language,
    InputMode inputMode
) {
    public SourcePayload {
        Objects.requireNonNull(sourceText, "sourceText must not be null");
        Objects.requireNonNull(sourceName, "sourceName must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(inputMode, "inputMode must not be null");
    }

    public static SourcePayload of(String sourceText, String sourceName, Language language, InputMode inputMode) {
        return new SourcePayload(sourceText, sourceName, language, inputMode);
    }

    public static SourcePayload singleLine(String sourceText, Language language) {
        return new SourcePayload(sourceText, "<single-line>", language, InputMode.SINGLE_LINE);
    }

    public static SourcePayload snippet(String sourceText, Language language) {
        return new SourcePayload(sourceText, "<snippet>", language, InputMode.CODE_SNIPPET);
    }

    public static SourcePayload file(String sourceText, String sourceName, Language language) {
        return new SourcePayload(sourceText, sourceName, language, InputMode.FILE_UPLOAD);
    }

    /**
     * Character count of the ingested source text.
     */
    public int characterCount() {
        return sourceText.length();
    }

    /**
     * Total number of lines in the source text.
     * Returns 0 for empty source text; 1 for a single line without line terminators.
     */
    public int lineCount() {
        if (sourceText.isEmpty()) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < sourceText.length(); i++) {
            char c = sourceText.charAt(i);
            if (c == '\r') {
                count++;
                if (i + 1 < sourceText.length() && sourceText.charAt(i + 1) == '\n') {
                    i++;
                }
            } else if (c == '\n') {
                count++;
            }
        }
        return count;
    }
}
