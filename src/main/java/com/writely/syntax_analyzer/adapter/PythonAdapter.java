package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.adapter.python.PythonParser;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Parser adapter for the Python programming language.
 */
public class PythonAdapter implements ParserAdapter {

    private final BiFunction<SourcePayload, List<Token>, ParseResult> customParser;
    private final PythonParser defaultParser;

    public PythonAdapter() {
        this(null);
    }

    public PythonAdapter(BiFunction<SourcePayload, List<Token>, ParseResult> customParser) {
        this.customParser = customParser;
        this.defaultParser = new PythonParser();
    }

    @Override
    public Language language() {
        return Language.PYTHON;
    }

    @Override
    public RuleCapabilities ruleCapabilities() {
        return RuleCapabilities.forPython();
    }

    @Override
    public ParseResult parse(SourcePayload payload, List<Token> tokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");
        if (payload.language() != Language.PYTHON) {
            throw new IllegalArgumentException("PythonAdapter only supports Python, but got: " + payload.language());
        }
        if (customParser != null) {
            return customParser.apply(payload, tokens);
        }
        return defaultParser.parse(payload, tokens);
    }
}
