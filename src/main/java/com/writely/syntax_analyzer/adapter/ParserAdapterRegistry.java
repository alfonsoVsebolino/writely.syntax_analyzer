package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry managing language parser adapters with file extension inference
 * and explicit language selection resolution.
 */
public class ParserAdapterRegistry {

    private final Map<Language, ParserAdapter> adapters = new ConcurrentHashMap<>();

    public ParserAdapterRegistry() {
        this(true);
    }

    public ParserAdapterRegistry(boolean registerDefaults) {
        if (registerDefaults) {
            register(new JavaAdapter());
            register(new PythonAdapter());
            register(new CppAdapter());
        }
    }

    public static ParserAdapterRegistry createDefault() {
        return new ParserAdapterRegistry(true);
    }

    public static ParserAdapterRegistry empty() {
        return new ParserAdapterRegistry(false);
    }

    public void register(ParserAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter must not be null");
        adapters.put(adapter.language(), adapter);
    }

    public boolean unregister(Language language) {
        Objects.requireNonNull(language, "language must not be null");
        return adapters.remove(language) != null;
    }

    public boolean hasAdapter(Language language) {
        return language != null && adapters.containsKey(language);
    }

    public Optional<ParserAdapter> getAdapter(Language language) {
        if (language == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(adapters.get(language));
    }

    public ParserAdapter getRequiredAdapter(Language language) {
        return getAdapter(language)
            .orElseThrow(() -> new IllegalArgumentException("No parser adapter registered for language: " + language));
    }

    public Optional<ParserAdapter> getAdapterForExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return Optional.empty();
        }
        Optional<Language> resolvedLang = Language.fromExtension(extension);
        if (resolvedLang.isPresent() && adapters.containsKey(resolvedLang.get())) {
            return Optional.of(adapters.get(resolvedLang.get()));
        }
        for (ParserAdapter adapter : adapters.values()) {
            if (adapter.supportsExtension(extension)) {
                return Optional.of(adapter);
            }
        }
        return Optional.empty();
    }

    public Optional<ParserAdapter> getAdapterForFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }
        Optional<Language> resolvedLang = Language.fromFileName(fileName);
        if (resolvedLang.isPresent() && adapters.containsKey(resolvedLang.get())) {
            return Optional.of(adapters.get(resolvedLang.get()));
        }
        String normalized = fileName.trim();
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < normalized.length() - 1) {
            String ext = normalized.substring(lastDot);
            return getAdapterForExtension(ext);
        }
        return Optional.empty();
    }

    /**
     * Resolves an adapter prioritizing explicit language selection over file extension inference.
     * If an explicit language is specified, it strictly overrides extension inference.
     * If no explicit language is specified, the language is inferred from the file name or path.
     *
     * @param explicitLanguage explicitly selected language, or null
     * @param fileNameOrPath file name or path for extension inference, or null
     * @return resolved adapter, or empty if no matching adapter exists
     */
    public Optional<ParserAdapter> resolveAdapter(Language explicitLanguage, String fileNameOrPath) {
        if (explicitLanguage != null) {
            return getAdapter(explicitLanguage);
        }
        return getAdapterForFileName(fileNameOrPath);
    }

    /**
     * Resolves an adapter for the given ingested source payload.
     */
    public Optional<ParserAdapter> resolveAdapter(SourcePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return resolveAdapter(payload.language(), payload.sourceName());
    }

    public ParserAdapter resolveRequiredAdapter(Language explicitLanguage, String fileNameOrPath) {
        return resolveAdapter(explicitLanguage, fileNameOrPath)
            .orElseThrow(() -> new IllegalArgumentException(
                "No parser adapter found for language=" + explicitLanguage + ", fileName=" + fileNameOrPath));
    }

    public ParserAdapter resolveRequiredAdapter(SourcePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return resolveAdapter(payload)
            .orElseThrow(() -> new IllegalArgumentException(
                "No parser adapter found for payload language=" + payload.language() + ", source=" + payload.sourceName()));
    }

    public Set<Language> registeredLanguages() {
        return Collections.unmodifiableSet(adapters.keySet());
    }

    public Collection<ParserAdapter> allAdapters() {
        return Collections.unmodifiableCollection(adapters.values());
    }

    public int size() {
        return adapters.size();
    }

    public void clear() {
        adapters.clear();
    }
}
