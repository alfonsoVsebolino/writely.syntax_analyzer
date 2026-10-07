package com.writely.syntax_analyzer.core.tokenization;

import com.writely.syntax_analyzer.domain.Language;

import java.util.Objects;

/**
 * Factory for resolving language-specific Tokenizer implementations.
 */
public final class TokenizerFactory {

    private TokenizerFactory() {
        // Utility class
    }

    /**
     * Resolves the appropriate tokenizer implementation for the given language.
     *
     * @param language the programming language, must not be null
     * @return the tokenizer instance
     */
    public static Tokenizer forLanguage(Language language) {
        Objects.requireNonNull(language, "language must not be null");
        return switch (language) {
            case JAVA -> new JavaTokenizer();
            case PYTHON -> new PythonTokenizer();
            case CPP -> new CppTokenizer();
        };
    }
}
