package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Root node representing a parsed C++ translation unit.
 */
public record CppTranslationUnit(
    String sourceName,
    List<CppAstNode> declarations,
    SourceSpan span
) implements CppAstNode {

    public CppTranslationUnit {
        Objects.requireNonNull(sourceName, "sourceName must not be null");
        declarations = declarations == null ? List.of() : List.copyOf(declarations);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = declarations.stream()
            .map(CppAstNode::toSyntaxNode)
            .toList();
        return SyntaxNode.of(
            "TranslationUnit",
            sourceName,
            span,
            children,
            Map.of("sourceName", sourceName, "language", "CPP")
        );
    }
}
