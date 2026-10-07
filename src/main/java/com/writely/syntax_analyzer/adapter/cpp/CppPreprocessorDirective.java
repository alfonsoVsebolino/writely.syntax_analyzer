package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a generic C++ preprocessor directive (e.g. #define, #pragma, #ifdef).
 */
public record CppPreprocessorDirective(
    String directive,
    SourceSpan span
) implements CppAstNode {

    public CppPreprocessorDirective {
        Objects.requireNonNull(directive, "directive must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.leaf(
            "PreprocessorDirective",
            directive.trim(),
            span,
            Map.of("directive", directive.trim())
        );
    }
}
