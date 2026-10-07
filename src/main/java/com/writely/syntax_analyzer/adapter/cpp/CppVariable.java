package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a C++ variable declaration (single or multiple declarators).
 */
public record CppVariable(
    String type,
    List<CppVarDeclarator> declarators,
    SourceSpan span
) implements CppAstNode {

    public CppVariable {
        Objects.requireNonNull(type, "type must not be null");
        declarators = declarators == null ? List.of() : List.copyOf(declarators);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = declarators.stream()
            .map(CppVarDeclarator::toSyntaxNode)
            .toList();
        String primaryName = declarators.isEmpty() ? type : declarators.get(0).name();
        Map<String, String> attrs = new HashMap<>();
        attrs.put("type", type);
        attrs.put("name", primaryName);
        return SyntaxNode.of("VariableDeclaration", primaryName, span, children, attrs);
    }
}
