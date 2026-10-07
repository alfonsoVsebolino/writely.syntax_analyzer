package com.writely.syntax_analyzer.core.tokenization;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;

import java.util.List;
import java.util.Objects;

/**
 * Composite tokenizer that delegates tokenization to the appropriate language tokenizer
 * based on the payload language.
 */
public class CompositeTokenizer implements Tokenizer {

    private final JavaTokenizer javaTokenizer;
    private final PythonTokenizer pythonTokenizer;
    private final CppTokenizer cppTokenizer;

    public CompositeTokenizer() {
        this(new JavaTokenizer(), new PythonTokenizer(), new CppTokenizer());
    }

    public CompositeTokenizer(JavaTokenizer javaTokenizer, PythonTokenizer pythonTokenizer, CppTokenizer cppTokenizer) {
        this.javaTokenizer = Objects.requireNonNull(javaTokenizer, "javaTokenizer must not be null");
        this.pythonTokenizer = Objects.requireNonNull(pythonTokenizer, "pythonTokenizer must not be null");
        this.cppTokenizer = Objects.requireNonNull(cppTokenizer, "cppTokenizer must not be null");
    }

    public static Tokenizer forLanguage(Language language) {
        return TokenizerFactory.forLanguage(language);
    }

    @Override
    public List<Token> tokenize(SourcePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return switch (payload.language()) {
            case JAVA -> javaTokenizer.tokenize(payload);
            case PYTHON -> pythonTokenizer.tokenize(payload);
            case CPP -> cppTokenizer.tokenize(payload);
        };
    }
}
