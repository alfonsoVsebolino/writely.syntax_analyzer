package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.core.tokenization.Tokenizer;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Universal contract for language-specific parser adapters.
 * Exposes language identity, supported extensions, tokenization and parsing entry points,
 * and language-specific rule capabilities.
 */
public interface ParserAdapter {

    /**
     * Target language identity for this adapter.
     */
    Language language();

    /**
     * Display name of the language.
     */
    default String displayName() {
        return language().displayName();
    }

    /**
     * File extensions supported by this adapter (including leading dot).
     */
    default Set<String> supportedExtensions() {
        return Set.copyOf(language().extensions());
    }

    /**
     * Tests whether this adapter supports the given file extension.
     */
    default boolean supportsExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return false;
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(".")) {
            normalized = "." + normalized;
        }
        for (String ext : supportedExtensions()) {
            if (ext.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Language-specific rule capabilities.
     */
    RuleCapabilities ruleCapabilities();

    /**
     * Tokenization entry point converting the payload into a list of tokens.
     */
    default List<Token> tokenize(SourcePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return Tokenizer.forLanguage(language()).tokenize(payload);
    }

    /**
     * Parsing entry point parsing the payload into a {@link ParseResult}.
     * Tokenizes the payload first and then delegates to {@link #parse(SourcePayload, List)}.
     */
    default ParseResult parse(SourcePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        List<Token> tokens = tokenize(payload);
        return parse(payload, tokens);
    }

    /**
     * Parsing entry point using pre-scanned tokens.
     */
    ParseResult parse(SourcePayload payload, List<Token> tokens);
}
