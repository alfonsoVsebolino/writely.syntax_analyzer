package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a C++ access specifier label (public:, protected:, private:).
 */
public record CppAccessSpecifier(
    String access,
    SourceSpan span
) implements CppAstNode {

    public CppAccessSpecifier {
        Objects.requireNonNull(access, "access must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.leaf(
            "AccessSpecifier",
            access + ":",
            span,
            Map.of("access", access)
        );
    }
}
