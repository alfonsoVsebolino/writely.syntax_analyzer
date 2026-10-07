package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single declarator in a C++ variable declaration.
 */
public record CppVarDeclarator(
    String name,
    CppExpr initializer,
    SourceSpan span
) implements CppAstNode {

    public CppVarDeclarator {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("name", name);
        List<SyntaxNode> children = initializer != null ? List.of(initializer.toSyntaxNode()) : List.of();
        return SyntaxNode.of("VariableDeclarator", name, span, children, attrs);
    }
}
