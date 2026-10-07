package com.writely.syntax_analyzer.core.tokenization;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;

import java.util.List;

/**
 * Contract for programming language tokenizers that convert source payloads
 * into discrete token streams with precise source coordinate tracking.
 */
public interface Tokenizer {

    /**
     * Tokenizes the ingested source payload into a stream of tokens.
     *
     * @param payload the source payload to tokenize, must not be null
     * @return an immutable list of scanned tokens
     */
    List<Token> tokenize(SourcePayload payload);

    /**
     * Resolves the appropriate tokenizer implementation for the specified language.
     *
     * @param language target programming language
     * @return language-specific tokenizer
     */
    static Tokenizer forLanguage(Language language) {
        return TokenizerFactory.forLanguage(language);
    }
}
