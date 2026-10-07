package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.adapter.java.JavaParser;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Parser adapter for the Java programming language.
 */
public class JavaAdapter implements ParserAdapter {

    private final BiFunction<SourcePayload, List<Token>, ParseResult> customParser;
    private final JavaParser defaultParser;

    public JavaAdapter() {
        this(null);
    }

    public JavaAdapter(BiFunction<SourcePayload, List<Token>, ParseResult> customParser) {
        this.customParser = customParser;
        this.defaultParser = new JavaParser();
    }

    @Override
    public Language language() {
        return Language.JAVA;
    }

    @Override
    public RuleCapabilities ruleCapabilities() {
        return RuleCapabilities.forJava();
    }

    @Override
    public ParseResult parse(SourcePayload payload, List<Token> tokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");
        if (payload.language() != Language.JAVA) {
            throw new IllegalArgumentException("JavaAdapter only supports Java, but got: " + payload.language());
        }
        if (customParser != null) {
            return customParser.apply(payload, tokens);
        }
        return defaultParser.parse(payload, tokens);
    }
}
