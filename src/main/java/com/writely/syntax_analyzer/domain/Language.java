package com.writely.syntax_analyzer.domain;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Supported programming languages for syntax analysis.
 */
public enum Language {
    JAVA("Java", List.of(".java")),
    PYTHON("Python", List.of(".py")),
    CPP("C++", List.of(".cpp", ".cxx", ".cc", ".h", ".hpp"));

    private final String displayName;
    private final List<String> extensions;

    Language(String displayName, List<String> extensions) {
        this.displayName = displayName;
        this.extensions = Collections.unmodifiableList(extensions);
    }

    /**
     * Human-readable display name.
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Canonical file extensions including leading dot.
     */
    public List<String> extensions() {
        return extensions;
    }

    /**
     * Primary canonical file extension.
     */
    public String primaryExtension() {
        return extensions.get(0);
    }

    /**
     * Resolves language from a source file name or path.
     */
    public static Optional<Language> fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }
        String normalized = fileName.trim().toLowerCase(Locale.ROOT);
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < normalized.length() - 1) {
            String ext = normalized.substring(lastDot);
            return fromExtension(ext);
        }
        return Optional.empty();
    }

    /**
     * Resolves language from a file extension (with or without leading dot).
     */
    public static Optional<Language> fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return Optional.empty();
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(".")) {
            normalized = "." + normalized;
        }
        for (Language lang : values()) {
            for (String ext : lang.extensions) {
                if (ext.equalsIgnoreCase(normalized)) {
                    return Optional.of(lang);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves language from enum name, display name, file extension, or file path.
     */
    public static Optional<Language> fromNameOrExtension(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        for (Language lang : values()) {
            if (lang.name().equalsIgnoreCase(trimmed) || lang.displayName.equalsIgnoreCase(trimmed)) {
                return Optional.of(lang);
            }
        }
        Optional<Language> byExt = fromExtension(trimmed);
        if (byExt.isPresent()) {
            return byExt;
        }
        return fromFileName(trimmed);
    }
}
