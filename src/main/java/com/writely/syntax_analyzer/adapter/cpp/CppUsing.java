package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a C++ using directive, using declaration, or type alias.
 */
public record CppUsing(
    String target,
    String kind,
    SourceSpan span
) implements CppAstNode {

    public CppUsing {
        Objects.requireNonNull(target, "target must not be null");
        kind = kind == null ? "using" : kind;
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        String nodeKind = "alias".equalsIgnoreCase(kind) ? "TypeAliasDeclaration" : "UsingDeclaration";
        return SyntaxNode.leaf(
            nodeKind,
            target,
            span,
            Map.of("target", target, "kind", kind)
        );
    }
}
