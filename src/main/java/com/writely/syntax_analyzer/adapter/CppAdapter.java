package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;
import com.writely.syntax_analyzer.domain.Token;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Parser adapter for the C++ programming language.
 */
public class CppAdapter implements ParserAdapter {

    private final BiFunction<SourcePayload, List<Token>, ParseResult> customParser;

    public CppAdapter() {
        this(null);
    }

    public CppAdapter(BiFunction<SourcePayload, List<Token>, ParseResult> customParser) {
        this.customParser = customParser;
    }

    @Override
    public Language language() {
        return Language.CPP;
    }

    @Override
    public RuleCapabilities ruleCapabilities() {
        return RuleCapabilities.forCpp();
    }

    @Override
    public ParseResult parse(SourcePayload payload, List<Token> tokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");
        if (payload.language() != Language.CPP) {
            throw new IllegalArgumentException("CppAdapter only supports C++, but got: " + payload.language());
        }
        if (customParser != null) {
            return customParser.apply(payload, tokens);
        }
        SourceSpan span = tokens.isEmpty()
            ? SourceSpan.point(SourceLocation.start())
            : SourceSpan.of(tokens.get(0).span().start(), tokens.get(tokens.size() - 1).span().end());
        SyntaxNode root = SyntaxNode.of("TranslationUnit", payload.sourceName(), span);
        return ParseResult.of(root, List.of(), null, tokens);
    }
}
