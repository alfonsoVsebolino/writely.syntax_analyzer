package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a C++ namespace declaration (named, nested, or anonymous).
 */
public record CppNamespace(
    String name,
    List<CppAstNode> members,
    SourceSpan span
) implements CppAstNode {

    public CppNamespace {
        Objects.requireNonNull(name, "name must not be null");
        members = members == null ? List.of() : List.copyOf(members);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = members.stream()
            .map(CppAstNode::toSyntaxNode)
            .toList();
        return SyntaxNode.of(
            "NamespaceDeclaration",
            name,
            span,
            children,
            Map.of("name", name)
        );
    }
}
